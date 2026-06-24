package com.edge.pulse.data.enums;

/** Accrual state for a (user, scale, window). COLLECTING until the completion predicate is met,
 *  then CONSOLIDATED once scored FINAL. */
public enum ScaleProgressState { COLLECTING, CONSOLIDATED }
