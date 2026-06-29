#!/usr/bin/env python3
"""
Generate demo-ready Pulse assessment import packages from the Beacon Red source
(`br/`) + the Phase-1 parity scoring configs.

For each test, writes samples/<slug>/:
  questions.csv       (verbatim copy of the BR question CSV — the importer's CsvReader
                       handles BOM/quotes/Arabic/image-markdown)
  answer_key.csv      (cognitive: header + ANS row of correct values; personality: header/ANS only)
  scoring_sheet.csv   (rendered from the parity config.json, proven format)
  images.zip          (ATD + LR only, from br/CA Images/<ATD|LR>)
  sample_responses.csv / expected_scores.csv   (demo data from the parity fixtures)
  README.md           (per-test)

Correctness guard: the generated PTI scoring_sheet is diffed against the committed
parity/pti/scoring_sheet.csv golden; mismatch aborts. Header/scale/key alignment is asserted.

Re-runnable. Run from anywhere: python3 samples/_generate.py
"""
import csv, json, io, os, sys, zipfile, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)                                   # Service/pulse/pulse
PULSE_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(REPO)))  # Documents/Pulse
BR = os.path.join(PULSE_ROOT, "br")
PARITY = os.path.join(REPO, "src/test/resources/psychometric/parity")
SCORING_COLS = ["rowType","name","parentName","scoreMethod","normStrategy","mean","sd",
                "tFactor","tOffset","tClipLo","tClipHi","compositeMethod","compositeBasis",
                "childScales","roundingScale","restricted","questionHeader","scaleName",
                "direction","itemStrategy","weight"]

TESTS = [
    dict(slug="pti-plus", br="PTI Plus 2.0.csv", parity="pti",
         type="PERSONALITY", timed=False, images=None, kind="personality"),
    dict(slug="adaptive-traits-profiler", br="Adaptive Traits Profiler (ATP) 2.0.csv", parity="atp",
         type="PERSONALITY", timed=False, images=None, kind="personality"),
    dict(slug="logical-reasoning", br="Logical Reasoning (CA.b) 2.0.csv", parity="ca",
         type="COGNITIVE", timed=True, images="LR", kind="cognitive",
         ca_scale="logical_reasoning_sum", ca_key="logical_key.csv", ca_resp="logical.csv"),
    dict(slug="verbal-reasoning", br="Verbal Reasoning (CA.a).csv", parity="ca",
         type="COGNITIVE", timed=True, images=None, kind="cognitive",
         ca_scale="verbal_reasoning_sum", ca_key="verbal_key.csv", ca_resp="verbal.csv"),
    dict(slug="numerical-reasoning", br="Numerical Reasoning (CA.a).csv", parity="ca",
         type="COGNITIVE", timed=True, images=None, kind="cognitive",
         ca_scale="numerical_reasoning_sum", ca_key="numerical_key.csv", ca_resp="numerical.csv"),
    dict(slug="attention-to-detail", br="Attention to Detail (CA.a).csv", parity="ca",
         type="COGNITIVE", timed=True, images="ATD", kind="cognitive",
         ca_scale="attention_to_detail_sum", ca_key="atd_key.csv", ca_resp="atd.csv"),
]


def read_text(path):
    with open(path, encoding="utf-8-sig") as f:
        return f.read()


def question_headers(questions_csv_text):
    rows = list(csv.reader(io.StringIO(questions_csv_text)))
    return [r[0].strip() for r in rows[1:] if r and r[0].strip()]


def normalize_questions(br_text):
    """The BR question CSVs are ragged — the header declares 4 options but some rows carry a
    5th (extra trailing columns with no header), which the importer's header-mapped reader drops.
    Re-emit with a header wide enough for the MAX option count, padding shorter rows, so every
    option is parsed. Preserves image markdown + Arabic; drops the BOM (importer handles either)."""
    rows = [r for r in csv.reader(io.StringIO(br_text)) if any(c.strip() for c in r)]
    data = rows[1:]
    max_opts = max(((len(r) - 3 + 2) // 3) for r in data) if data else 4
    header = ["header", "questionEN", "questionAR"]
    for k in range(1, max_opts + 1):
        header += [f"answerEN{k}", f"answerAR{k}", f"value{k}"]
    width = len(header)
    out = io.StringIO()
    w = csv.writer(out)
    w.writerow(header)
    for r in data:
        w.writerow((r + [""] * width)[:width])
    return out.getvalue()


def scoring_rows_personality(cfg):
    """Render scale + item rows from a PTI/ATP-style config (per-scale normal/reversed)."""
    item_strategy = cfg["itemStrategy"]
    tf, to, tlo, thi = cfg["tFactor"], cfg["tOffset"], cfg["tClipLo"], cfg["tClipHi"]
    scales = cfg["scales"]
    composites = [s for s in scales if "children" in s]
    leaves = [s for s in scales if "children" not in s and "precomputed" not in s]
    parent_of = {child: c["name"] for c in composites for child in c["children"]}

    def base(name, parent="", composite=None, children=None):
        r = {k: "" for k in SCORING_COLS}
        sc = next(s for s in scales if s["name"] == name)
        r.update(rowType="scale", name=name, parentName=parent,
                 scoreMethod=sc.get("scoreMethod", "SUM"), normStrategy="PARAMETRIC",
                 mean=fmt(sc["mean"]), sd=fmt(sc["sd"]),
                 tFactor=str(tf), tOffset=str(to), tClipLo=str(tlo), tClipHi=str(thi),
                 restricted="false")
        if composite:
            r.update(compositeMethod=composite, childScales=";".join(children))
        return r

    rows = []
    for s in leaves:
        rows.append(base(s["name"], parent=parent_of.get(s["name"], "")))
    for c in composites:
        rows.append(base(c["name"], composite="AGGREGATE_OF_ITEMS", children=c["children"]))
    for s in leaves:
        for q in s.get("normal", []):
            rows.append(item_row(q, s["name"], "FORWARD", item_strategy))
        for q in s.get("reversed", []):
            rows.append(item_row(q, s["name"], "REVERSE", item_strategy))
    return rows


def scoring_rows_cognitive(cfg, scale_name, headers):
    """One sum scale; every question maps to it via ANSWER_KEY_SINGLE."""
    sub = next(s for s in cfg["subScales"] if s["name"] == scale_name)
    tf, to, tlo, thi = cfg["tFactor"], cfg["tOffset"], cfg["tClipLo"], cfg["tClipHi"]
    r = {k: "" for k in SCORING_COLS}
    r.update(rowType="scale", name=scale_name, parentName="", scoreMethod="SUM",
             normStrategy="PARAMETRIC", mean=fmt(sub["mean"]), sd=fmt(sub["sd"]),
             tFactor=str(tf), tOffset=str(to), tClipLo=str(tlo), tClipHi=str(thi),
             restricted="false")
    rows = [r]
    for q in headers:
        rows.append(item_row(q, scale_name, "FORWARD", "ANSWER_KEY_SINGLE"))
    return rows


def item_row(q, scale, direction, strategy):
    r = {k: "" for k in SCORING_COLS}
    r.update(rowType="item", questionHeader=q, scaleName=scale,
             direction=direction, itemStrategy=strategy, weight="1")
    return r


def fmt(x):
    """Match the golden's numeric rendering (no trailing .0 churn; keep as given)."""
    if isinstance(x, float):
        s = repr(x)
        return s
    return str(x)


def write_scoring(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=SCORING_COLS)
        w.writeheader()
        for r in rows:
            w.writerow(r)


def build_answer_key(t, headers):
    """Cognitive: read parity ca/<key> ANS row → header,Q.. / ANS,v.. . Personality: header/ANS."""
    if t["kind"] != "cognitive":
        return "header\nANS\n"
    key_path = os.path.join(PARITY, t["parity"], t["ca_key"])
    rows = list(csv.reader(open(key_path, encoding="utf-8-sig")))
    hdr = rows[0]
    ans = next(r for r in rows[1:] if r and r[0].strip().upper() == "ANS")
    qcols = [(c, hdr[i]) for i, c in enumerate(hdr) if c.startswith("Q")]
    # keep only question ids present in the test's questions.csv, in question order
    present = [h for h in headers if h in {c for c, _ in qcols}]
    idx = {hdr[i]: i for i in range(len(hdr))}
    out = io.StringIO()
    w = csv.writer(out)
    w.writerow(["header"] + present)
    w.writerow(["ANS"] + [ans[idx[q]] for q in present])
    return out.getvalue()


def subset_rows(path, n=4):
    rows = list(csv.reader(open(path, encoding="utf-8-sig")))
    return [rows[0]] + rows[1:1 + n]


def write_csv_rows(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as f:
        csv.writer(f).writerows(rows)


def main():
    # PTI golden guard first
    pti = next(t for t in TESTS if t["slug"] == "pti-plus")
    cfg = json.load(open(os.path.join(PARITY, "pti", "config.json")))
    gen = io.StringIO()
    w = csv.DictWriter(gen, fieldnames=SCORING_COLS); w.writeheader()
    for r in scoring_rows_personality(cfg):
        w.writerow(r)
    golden = read_text(os.path.join(PARITY, "pti", "scoring_sheet.csv"))
    if norm(gen.getvalue()) != norm(golden):
        print("GOLDEN MISMATCH (PTI scoring_sheet). Aborting — generator not trustworthy.")
        dump_diff(gen.getvalue(), golden)
        sys.exit(1)
    print("✓ PTI scoring_sheet matches golden parity fixture")

    for t in TESTS:
        out = os.path.join(HERE, t["slug"])
        os.makedirs(out, exist_ok=True)
        cfg = json.load(open(os.path.join(PARITY, t["parity"], "config.json")))
        # 1. questions.csv (normalized from BR — widens ragged 5-option rows so no option is dropped)
        qtext = normalize_questions(read_text(os.path.join(BR, "Latest Versions", t["br"])))
        with open(os.path.join(out, "questions.csv"), "w", encoding="utf-8") as f:
            f.write(qtext)
        headers = question_headers(qtext)
        # 2. answer_key.csv
        with open(os.path.join(out, "answer_key.csv"), "w", encoding="utf-8") as f:
            f.write(build_answer_key(t, headers))
        # 3. scoring_sheet.csv
        if t["kind"] == "personality":
            rows = scoring_rows_personality(cfg)
        else:
            rows = scoring_rows_cognitive(cfg, t["ca_scale"], headers)
        write_scoring(os.path.join(out, "scoring_sheet.csv"), rows)
        # validate alignment
        item_qs = {r["questionHeader"] for r in rows if r["rowType"] == "item"}
        missing = item_qs - set(headers)
        assert not missing, f"{t['slug']}: scoring items reference unknown questions {missing}"
        # 4. images.zip
        if t["images"]:
            src = os.path.join(BR, "CA Images", t["images"])
            zpath = os.path.join(out, "images.zip")
            with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
                for fn in sorted(os.listdir(src)):
                    if fn.lower().endswith(".png"):
                        z.write(os.path.join(src, fn), fn)
        # 5. sample data
        if t["kind"] == "cognitive":
            resp = os.path.join(PARITY, "ca", t["ca_resp"])
            write_csv_rows(os.path.join(out, "sample_responses.csv"), subset_rows(resp))
            exp = list(csv.reader(open(os.path.join(PARITY, "ca", "expected.csv"), encoding="utf-8-sig")))
            col = t["ca_scale"] + "_Sten"
            ci = exp[0].index(col) if col in exp[0] else None
            ui = exp[0].index("userName")
            erows = [["userName", col]] + [[r[ui], r[ci]] for r in exp[1:5]] if ci is not None else exp[:5]
            write_csv_rows(os.path.join(out, "expected_scores.csv"), erows)
        else:
            resp = os.path.join(PARITY, t["parity"], "responses.csv")
            if os.path.exists(resp):
                write_csv_rows(os.path.join(out, "sample_responses.csv"), subset_rows(resp))
            exp = os.path.join(PARITY, t["parity"], "expected.csv")
            if os.path.exists(exp):
                write_csv_rows(os.path.join(out, "expected_scores.csv"), subset_rows(exp))
        print(f"✓ {t['slug']:28s} questions={len(headers):3d} scales={sum(1 for r in rows if r['rowType']=='scale')} "
              f"items={sum(1 for r in rows if r['rowType']=='item')}"
              f"{' +images.zip' if t['images'] else ''}")
    print("\nDone. Packages in samples/<slug>/")


def norm(s):
    # compare ignoring trailing whitespace + line endings
    return [ln.rstrip("\r") for ln in s.strip().splitlines()]


def dump_diff(a, b):
    import difflib
    for ln in list(difflib.unified_diff(norm(b), norm(a), "golden", "generated", lineterm=""))[:40]:
        print(ln)


if __name__ == "__main__":
    main()
