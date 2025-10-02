package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        var tokens = new ArrayList<Token>();
        while (chars.has(0)) {
            //TODO: Skip whitespace/comments
            if (chars.peek("[ \b\n\r\t]")) {
                lexWhitespace();
            } else if (chars.peek("/", "/")) {
                lexComment();
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    private void lexWhitespace() {
        while (chars.match("[ \b\n\r\t]")) {}
        chars.emit();
    }

    private void lexComment() {
        while (chars.has(0) && !chars.peek("\n")) {
            chars.match(".");
        }
        chars.emit();
    }

    private Token lexToken() throws LexException {
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (chars.peek("[+-]", "[0-9]") || chars.peek("[0-9]")) {
            return lexNumber();
        } else if (chars.peek("'")) {
            return lexCharacter();
        } else if (chars.peek("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }

    }

    private Token lexIdentifier() {
        chars.match("[A-Za-z_]");

        while (chars.peek("[A-Za-z0-9_-]")) {
            chars.match("[A-Za-z0-9_-]");
        }
        return new Token(Token.Type.IDENTIFIER, chars.emit());
    }

    private Token lexNumber() throws LexException {
        boolean tf = false;
        chars.match("[+-]");
        while (chars.match("[0-9]")) {}
        if (chars.match("\\.")) {
            if (chars.peek("[0-9]")) {
                tf = true;

                while (chars.match("[0-9]")) {}
            } else {
                chars.index--;
                chars.length--;
                String i = chars.emit();
                return new Token(Token.Type.INTEGER, i);
            }
        }
        if (chars.match("[eE]")) {
            chars.match("[+-]");
            if (!chars.peek("[0-9]")) {
                chars.index--;
                chars.length--;
                String i = chars.emit();
                return new Token(Token.Type.INTEGER, i);
            }
            while (chars.match("[0-9]")) {}
        }
        String val = chars.emit();
        if (tf) {
            return new Token(Token.Type.DECIMAL, val);
        } else {
            return new Token(Token.Type.INTEGER, val);
        }
    }

    private Token lexCharacter() throws LexException {
        chars.match("'");
        if (!chars.has(0)) {
            throw new LexException("Invalid character", chars.index);
        }
        if (chars.peek("\\\\")) {
            lexEscape();
        } else if (chars.peek("[^'\n\r\\\\]")) {
            chars.match(".");
        } else {
            throw new LexException("Invalid character", chars.index);
        }
        if (!chars.match("'")) {
            throw new LexException("Invalid character", chars.index);
        }

        String val = chars.emit();
        if (val.length() < 3 || val.length() > 4) {
            throw new LexException("Invalid character", chars.index);
        }
        return new Token(Token.Type.CHARACTER, val);
    }

    private Token lexString() throws LexException {
        //string ::= '"' ([^"\n\r\\] | escape)* '"'
        chars.match("\"");
        while (chars.has(0) && !chars.peek("\"")) {
            if (chars.peek("\\\\")) {
                lexEscape();
            } else if (chars.peek("[^\"\\\\\n\r]")) {
                chars.match(".");
            } else {
                throw new LexException("Invalid string", chars.index);
            }
        }
        if (!chars.match("\"")) {
            throw new LexException("Invalid string", chars.index);
        }
        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() throws LexException {
        if (!chars.match("\\\\")) {
            throw new LexException("Invalid escape", chars.index);
        }
        //

        if (!chars.match("[bnrt'\"\\\\]")) {
            throw new LexException("Invalid escape", chars.index);
        }
    }

    public Token lexOperator() {
        //operator ::= [<>!=] '='? | [^A-Za-z_0-9'" \b\n\r\t]
        if (chars.match("[<>!=]", "=")) {

            return new Token(Token.Type.OPERATOR, chars.emit());
        } else if (chars.match("[^A-Za-z_0-9'\" \b\n\r\t]")) {

            return new Token(Token.Type.OPERATOR, chars.emit());
        } else {
            chars.match(".");

            return new Token(Token.Type.OPERATOR, chars.emit());
        }
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next character(s) match their corresponding
         * pattern(s). Each pattern is a regex matching ONE character, e.g.:
         *  - peek("/") is valid and will match the next character
         *  - peek("/", "/") is valid and will match the next two characters
         *  - peek("/+") is conceptually invalid, but will match one character
         *  - peek("//") is strictly invalid as it can never match one character
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
