package org.openclover.core.reporters;

import clover.antlr.CommonAST;
import clover.antlr.RecognitionException;
import clover.antlr.TokenStreamException;

import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.openclover.core.reporters.CalcLexer;
import org.openclover.core.reporters.CalcParser;
import org.openclover.core.reporters.CalcTreeWalker;
import org.openclover.core.api.registry.BlockMetrics;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;

/**
 */
public class ExpressionEvaluator {

    public static double eval(String expr, BlockMetrics metrics, String title) throws CloverException {

        CalcParser parser = parse(expr, title);
        CommonAST t = (CommonAST) parser.getAST();

        // Print the resulting tree out in LISP notation
        if (Logger.isDebug()) {
            Logger.getInstance().debug(t.toStringTree());
        }
        CalcTreeWalker walker = new CalcTreeWalker();

        try {
            // Traverse the tree created by the parser
            return walker.expr(t, metrics);

        } catch (RecognitionException e) {
            Logger.getInstance().debug(e.getMessage(), e);
            throw wrapException(expr, e);
        }

    }

    public static CalcParser parse(String expr, String title) throws CloverException {
        InputStream in = new ByteArrayInputStream(expr.getBytes());
        CalcLexer lexer = new CalcLexer(in);
        lexer.setFilename(title);
        CalcParser parser = new CalcParser(lexer);
        parser.setFilename(title);
        try {
            parser.expr();
            return parser;
        } catch (RecognitionException | TokenStreamException e) {
            Logger.getInstance().debug(e.getMessage(), e);
            throw wrapException(expr, e);
        }
    }

    public static void validate(String expr, String title) throws CloverException {
        try {
            new CalcTreeWalker().validate(parse(expr, title).getAST());
        } catch (RecognitionException e) {
            Logger.getInstance().debug(e.getMessage(), e);
            throw wrapException(expr, e);
        }
    }

    private static CloverException wrapException(String expr, Exception e) {
        return new CloverException(e.getMessage() + " in expression '" + expr + "'", e);
    }
}
