package plc.project.parser;

import com.google.common.base.Preconditions;
import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        if (tokens.peek("LET")) {
            return parseLetStmt();
        }
        else if (tokens.peek("DEF")) {
            return parseDefStmt();
        }
        else if (tokens.peek("IF")) {
            return parseIfStmt();
        }
        else if (tokens.peek("FOR")) {
            return parseForStmt();
        }
        else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        }
        else {
            return parseExpressionOrAssignmentStmt();
        }

    }

    private Ast.Stmt parseLetStmt() throws ParseException {
        if (!tokens.match("LET")) {

            throw new ParseException("invalid LET", tokens.getNext());
        }
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("invalid identifier.", tokens.getNext());
        }

        String name = tokens.get(0).literal();
        tokens.match(Token.Type.IDENTIFIER);

        Optional<Ast.Expr> val = Optional.empty();

        if (tokens.match("=")) {
            val = Optional.of(parseExpr());
        }

        if (!tokens.match(";")) {
            throw new ParseException("invalid ;", tokens.getNext());
        }

        return new Ast.Stmt.Let(name, val);
    }

    private Ast.Stmt parseDefStmt() throws ParseException {
        if (!tokens.match("DEF")) {

            throw new ParseException("invalid DEF", tokens.getNext());
        }
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("invalid identifier.", tokens.getNext());
        }

        String name = tokens.get(0).literal();
        tokens.match(Token.Type.IDENTIFIER);

        if (!tokens.match("(")) {
            throw new ParseException("invalid (", tokens.getNext());
        }

        List<String> parameter = new ArrayList<>();

        if (!tokens.peek(")")) {
            if (!tokens.peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("invalid parameter", tokens.getNext());
            }
            parameter.add(tokens.get(0).literal());
            tokens.match(Token.Type.IDENTIFIER);

            while (tokens.match(",")) {
                if (!tokens.peek(Token.Type.IDENTIFIER)) {
                    throw new ParseException("invalid parameter", tokens.getNext());
                }
                parameter.add(tokens.get(0).literal());
                tokens.match(Token.Type.IDENTIFIER);

            }
        }
        if (!tokens.match(")")) {
            throw new ParseException("invalid )", tokens.getNext());
        }
        if (!tokens.match("DO")) {
            throw new  ParseException("invalid DO", tokens.getNext());
        }
        List<Ast.Stmt> l = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END")) {
            l.add(parseStmt());
        }
        if (!tokens.match("END")) {
            throw new ParseException("invalid END", tokens.getNext());
        }
        return new Ast.Stmt.Def(name, parameter, l);


    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        if (!tokens.match("IF")) {
            throw new ParseException("invalid IF", tokens.getNext());
        }

        Ast.Expr cond = parseExpr();

        if (!tokens.match("DO")) {
            throw new ParseException("invalid DO", tokens.getNext());
        }

        List<Ast.Stmt> l = new ArrayList<>();

        while (tokens.has(0) && !tokens.peek("ELSE") && !tokens.peek("END")) {
            l.add(parseStmt());
        }

        List<Ast.Stmt> l2 = new ArrayList<>();
        if (tokens.match("ELSE")) {
            while (tokens.has(0) && !tokens.peek("END")) {
                l2.add(parseStmt());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("invalid END", tokens.getNext());
        }
        return new Ast.Stmt.If(cond, l, l2);

    }

    private Ast.Stmt parseForStmt() throws ParseException {
        if (!tokens.match("FOR")) {
            throw new ParseException("invalid FOR", tokens.getNext());
        }
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("invalid identifier", tokens.getNext());
        }
        String name = tokens.get(0).literal();
        tokens.match(Token.Type.IDENTIFIER);

        if (!tokens.match("IN")) {
            throw new ParseException("invalid IN", tokens.getNext());
        }

        Ast.Expr exp = parseExpr();

        if (!tokens.match("DO")) {
            throw new ParseException("invalid DO", tokens.getNext());
        }
        List<Ast.Stmt> l = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END")) {
            l.add(parseStmt());
        }
        if (!tokens.match("END")) {
            throw new ParseException("invalid END", tokens.getNext());
        }

        return new Ast.Stmt.For(name, exp, l);
    }

    private Ast.Stmt parseReturnStmt() throws ParseException {
        if (!tokens.match("RETURN")) {
            throw new  ParseException("invalid RETURN", tokens.getNext());
        }
        Optional<Ast.Expr> val = Optional.empty();

        if (!tokens.peek("IF") && !tokens.peek(";")) {
            val = Optional.of(parseExpr());
        }
        if (tokens.match("IF")) {
            Ast.Expr cond = parseExpr();
            if (!tokens.match(";")) {
                throw new ParseException("invalid ;", tokens.getNext());
            }
            return new Ast.Stmt.If(cond, List.of(new Ast.Stmt.Return(val)), List.of());
        }
        if (!tokens.match(";")) {
            throw new ParseException("invalid ;", tokens.getNext());
        }
        return new Ast.Stmt.Return(val);

    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr l = parseExpr();

        Ast.Stmt result;

        if (tokens.match("=")) {
            Ast.Expr r = parseExpr();
            result = new Ast.Stmt.Assignment(l, r);
        }
        else {
            result = new Ast.Stmt.Expression(l);
        }
        if (!tokens.match(";")) {
            throw new ParseException("invalid ;", tokens.getNext());
        }
        return result;
    }

    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr l = parseComparisonExpr();

        while (tokens.peek("AND") || tokens.peek("OR")) {
            String o = tokens.get(0).literal();

            tokens.match(o);
            Ast.Expr r = parseComparisonExpr();

            l = new Ast.Expr.Binary(o, l, r);
        }
        return l;

    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr l = parseAdditiveExpr();

        while (tokens.peek(">") || tokens.peek("<") || tokens.peek(">=") || tokens.peek("<=") || tokens.peek("==") || tokens.peek("!=")) {
            String o = tokens.get(0).literal();
            tokens.match(o);

            Ast.Expr r = parseAdditiveExpr();
            l = new Ast.Expr.Binary(o, l, r);
        }
        return l;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr l = parseMultiplicativeExpr();
        while (tokens.peek("+") || tokens.peek("-")) {
            String o = tokens.get(0).literal();
            tokens.match(o);
            Ast.Expr r = parseMultiplicativeExpr();
            l = new Ast.Expr.Binary(o, l, r);
        }
        return l;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr l = parseSecondaryExpr();

        while (tokens.peek("*") || tokens.peek("/")) {
            String o = tokens.get(0).literal();
            tokens.match(o);
            Ast.Expr r = parseSecondaryExpr();
            l = new Ast.Expr.Binary(o, l, r);
        }
        return l;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        Ast.Expr expr = parsePrimaryExpr();

        while (tokens.peek(".")) {
            expr = parsePropertyOrMethod(expr);
        }

        return expr;
    }

    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        if (!tokens.match(".")) {
            throw new ParseException("Missing .", tokens.getNext());
        }
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing identifier.", tokens.getNext());
        }
        String name = tokens.get(0).literal();

        tokens.match(Token.Type.IDENTIFIER);

        if (tokens.match("(")) {
            List<Ast.Expr> a = new ArrayList<>();
            if (!tokens.peek(")")) {
                a.add(parseExpr());

                while (tokens.match(",")) {
                    a.add(parseExpr());
                }
            }
            if (!tokens.match(")")) {
                throw new ParseException("Missing ).", tokens.getNext());
            }
            return new Ast.Expr.Method(receiver, name, a);
        }
        else {
            return new Ast.Expr.Property(receiver, name);
        }
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (tokens.match("NIL")) return new Ast.Expr.Literal(null);
        if (tokens.match("TRUE")) return new Ast.Expr.Literal(Boolean.TRUE);
        if (tokens.match("FALSE")) return new Ast.Expr.Literal(Boolean.FALSE);

        if (tokens.peek(Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL) || tokens.peek(Token.Type.CHARACTER)|| tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();
        }
        else if (tokens.peek("(")) {
            return parseGroupExpr();
        }

        else if (tokens.peek("OBJECT")) {
            return parseObjectExpr();
        }

        else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        }

        throw new ParseException("Missing primary expression.", tokens.getNext());
    }

    private Ast.Expr parseLiteralExpr() throws ParseException {
        if (tokens.match("NIL")) {
            return new Ast.Expr.Literal(null);
        }
        else if (tokens.match("TRUE")) {
            return new  Ast.Expr.Literal(Boolean.TRUE);
        }
        else if (tokens.match("FALSE")) {
            return new  Ast.Expr.Literal(Boolean.FALSE);
        }


        else if (tokens.peek(Token.Type.INTEGER)) {
            String l = tokens.get(0).literal();
            tokens.match(Token.Type.INTEGER);
            try {
                if (l.contains("e") || l.contains("E")) {

                    //  Hint: Maybe there's a way to utilize BigDecimal?
                    BigDecimal b = new BigDecimal(l);

                    return new Ast.Expr.Literal(b.toBigIntegerExact());
                }
                return new Ast.Expr.Literal(new BigInteger(l));
            }
            catch (ArithmeticException  | NumberFormatException err) {
                throw new ParseException("Invalid integer", tokens.getNext());
            }
        }
        else if (tokens.peek(Token.Type.DECIMAL)) {
            String l = tokens.get(0).literal();
            tokens.match(Token.Type.DECIMAL);

            try {
                return new  Ast.Expr.Literal(new BigDecimal(l));
            }
            catch (NumberFormatException err) {
                throw new ParseException("Invalid decimal", tokens.getNext());
            }
        }
        else if (tokens.peek(Token.Type.CHARACTER)) {
            String l = tokens.get(0).literal();
            tokens.match(Token.Type.CHARACTER);
            String i = l.substring(1, l.length() - 1);
            Character val;

            if (i.startsWith("\\")) {
                if (i.length() != 2) {
                    throw new ParseException("Invalid character", tokens.getNext());
                }
                char esc = i.charAt(1);
                switch (esc) {
                    case 'b':  val = '\b'; break;
                    case 'n':  val = '\n'; break;
                    case 'r':  val = '\r'; break;
                    case 't':  val = '\t'; break;
                    case '\'': val = '\''; break;
                    case '\"': val = '\"'; break;
                    case '\\': val = '\\'; break;
                    default: throw new ParseException("Invalid character", tokens.getNext());
                }
            }
            else {
                if (i.length() != 1) throw new ParseException("Invalid character", tokens.getNext());
                val = i.charAt(0);
            }
            return new Ast.Expr.Literal(val);


        }
        else if (tokens.peek(Token.Type.STRING)) {
            String l = tokens.get(0).literal();
            tokens.match(Token.Type.STRING);

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < l.length() - 1; i++) {
                char c = l.charAt(i);

                if (c != '\\') {
                    sb.append(c);
                }
                else {
                    if (i + 1 >= l.length() - 1) {
                        throw new ParseException("Invalid string", tokens.getNext());
                    }

                    char esc = l.charAt(++i);
                    switch (esc) {
                        case 'b':  sb.append('\b'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case '\'': sb.append('\''); break;
                        case '\"': sb.append('\"'); break;
                        case '\\': sb.append('\\'); break;
                        default: throw new ParseException("Invalid string", tokens.getNext());
                    }
                }
            }
            return new Ast.Expr.Literal(sb.toString());
        }
        throw new ParseException("Expected literal", tokens.getNext());
    }

    private Ast.Expr parseGroupExpr() throws ParseException {
        if (!tokens.match("(")) {
            throw new ParseException("Expected '('", tokens.getNext());

        }
        Ast.Expr i = parseExpr();


        if (!tokens.match(")")) {
            throw new ParseException("Expected ')'", tokens.getNext());
        }
        return new Ast.Expr.Group(i);
    }

    private Ast.Expr parseObjectExpr() throws ParseException {

        // checkpoint 2
        if (!tokens.match("OBJECT")) {
            throw new ParseException("invalid OBJECT", tokens.getNext());
        }

        Optional<String> name = Optional.empty();

        if (tokens.peek(Token.Type.IDENTIFIER) && !tokens.peek("DO")) {


            name = Optional.of(tokens.get(0).literal());
            tokens.match(Token.Type.IDENTIFIER);
        }

        if (!tokens.match("DO")) {
            throw new ParseException("invalid DO", tokens.getNext());
        }

        List<Ast.Stmt.Let> field = new ArrayList<>();

        List<Ast.Stmt.Def> method = new ArrayList<>();
        while (tokens.peek("LET")) {
            Ast.Stmt l = parseLetStmt();
            field.add((Ast.Stmt.Let) l);
        }

        while (tokens.peek("DEF")) {
            Ast.Stmt d = parseDefStmt();
            method.add((Ast.Stmt.Def) d);
        }

        if (!tokens.match("END")) {
            throw new ParseException("invalid END", tokens.getNext());
        }
        return new Ast.Expr.ObjectExpr(name, field, method);

    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        if (!tokens.peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing IDENTIFIER", tokens.getNext());
        }

        String name = tokens.get(0).literal();
        tokens.match(Token.Type.IDENTIFIER);

        if (tokens.match("(")) {
            List<Ast.Expr> a = new ArrayList<>();
            if (!tokens.peek(")")) {
                a.add(parseExpr());

                while (tokens.match(",")) {
                    a.add(parseExpr());
                }
            }
            if (!tokens.match(")")) {
                throw new ParseException("Missing ')'", tokens.getNext());
            }
            return new Ast.Expr.Function(name, a);

        }
        else {
            return new Ast.Expr.Variable(name);
        }
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
