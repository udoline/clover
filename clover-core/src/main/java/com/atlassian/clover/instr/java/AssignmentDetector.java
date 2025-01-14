package com.atlassian.clover.instr.java;

/**
 * Reads subsequent tokens from the expression and searches for the "=" assignment operator.
 */
public class AssignmentDetector implements CloverTokenConsumer {
    private boolean containsAssign = false;

    @Override
    public void accept(CloverToken token) {
        if (token.getType() == JavaTokenTypes.ASSIGN) {
            containsAssign = true;
        }
    }

    public boolean containsAssign() {
        return containsAssign;
    }
}
