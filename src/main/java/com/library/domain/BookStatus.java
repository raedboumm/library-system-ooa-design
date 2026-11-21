package com.library.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The states of a physical book copy: a direct translation of the
 * state diagram in docs/06-state-diagrams.md.
 *
 * Each state lists the states it may move to. Any other move is illegal
 * and is refused by BookCopy.
 */
public enum BookStatus {
    AVAILABLE, RESERVED, ISSUED, OVERDUE, RETURNED, DAMAGED, LOST, WITHDRAWN;

    /** Allowed next states (the arrows of the state diagram). */
    public Set<BookStatus> allowedNext() {
        switch (this) {
            case AVAILABLE: return EnumSet.of(ISSUED, RESERVED, WITHDRAWN);
            case RESERVED:  return EnumSet.of(ISSUED, AVAILABLE);
            case ISSUED:    return EnumSet.of(ISSUED, OVERDUE, RETURNED, LOST); // ISSUED->ISSUED = renew
            case OVERDUE:   return EnumSet.of(RETURNED, LOST);
            case RETURNED:  return EnumSet.of(AVAILABLE, RESERVED, DAMAGED);
            case DAMAGED:   return EnumSet.of(AVAILABLE, WITHDRAWN);
            case LOST:      return EnumSet.of(AVAILABLE, WITHDRAWN);
            case WITHDRAWN: return EnumSet.noneOf(BookStatus.class);          // final state
            default:        throw new AssertionError(this);
        }
    }

    public boolean canMoveTo(BookStatus next) {
        return allowedNext().contains(next);
    }
}
