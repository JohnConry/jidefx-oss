/*
 * @(#)DateFieldPatternVerifier.java 5/19/2013
 *
 * Copyright 2002 - 2013 JIDE Software Inc. All rights reserved.
 */

package jidefx.scene.control.field.verifier;

import java.util.Calendar;
import java.util.Date;

/**
 * {@code BaseCalendarFieldPatternVerifier} is an abstract implementation of the pattern verifier for the Date
 * type. It can verify a Calendar field.
 */
public abstract class DateFieldPatternVerifier extends PatternVerifier<Date> implements PatternVerifier.Value<Date, Date>,
        PatternVerifier.Adjustable<Integer>, PatternVerifier.Range<Integer> {
    private final int field;
    private final Calendar value;
    private final int min;
    private final int max;
    private boolean minMaxSet = true;

    public DateFieldPatternVerifier(int field) {
        this(field, Calendar.getInstance().getMinimum(field), Calendar.getInstance().getMaximum(field));
        minMaxSet = false;
    }

    DateFieldPatternVerifier(int field, int min, int max) {
        this.field = field;
        this.min = min;
        this.max = max;
        value = Calendar.getInstance();
    }

    @Override
    public Integer getMin() {
        return !minMaxSet && value != null ? value.getActualMinimum(field) : min;
    }

    @Override
    public Integer getMax() {
        return !minMaxSet && value != null ? value.getActualMaximum(field) : max;
    }

    @Override
    public Integer getEnd(Integer current) {
        if (value != null) {
            value.set(field, getMax());
        }
        return null;
    }

    @Override
    public Integer getHome(Integer current) {
        if (value != null) {
            value.set(field, getMin());
        }
        return null;
    }

    @Override
    public Integer getPreviousPage(Integer current, boolean restart) {
        if (value != null) {
            if (restart) {
                value.add(field, -10);
            }
            else {
                if (value.get(field) - getMin() > 10) {
                    value.add(field, -10);
                }
                else {
                    value.set(field, getMin());
                }
            }
        }
        return null;
    }

    @Override
    public Integer getNextPage(Integer current, boolean restart) {
        if (value != null) {
            if (restart) {
                value.add(field, 10);
            }
            else {
                if (getMax() - value.get(field) > 10) {
                    value.add(field, 10);
                }
                else {
                    value.set(field, getMax());
                }
            }
        }
        return null;
    }

    @Override
    public Integer getNextValue(Integer current, boolean restart) {
        if (value != null) {
            if (restart) {
                value.add(field, 1);
            }
            else {
                if (value.get(field) != getMax()) {
                    value.add(field, 1);
                }
            }
        }
        return null;
    }

    @Override
    public Integer getPreviousValue(Integer current, boolean restart) {
        if (value != null) {
            if (restart) {
                value.add(field, -1);
            }
            else {
                if (value.get(field) != getMin()) {
                    value.add(field, -1);
                }
            }
        }
        return null;
    }

    @Override
    public void setFieldValue(Date fieldValue) {
        if (fieldValue != null) {
            value.setTime(fieldValue);
        }
    }

    @Override
    public Date getFieldValue() {
        return value.getTime();
    }

    @Override
    public Date toTargetValue(Date fieldValue) {
        return fieldValue;
    }

    @Override
    public Date fromTargetValue(Date existingValue, Date value) {
        return value;
    }
}
