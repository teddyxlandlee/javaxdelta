package xland.ioutils.xdelta.wrapper;

import java.io.IOException;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class EscapedIO {
    private static final char ESCAPE_CHAR = '\\';
    private static final char SINGLE_QUOTE = '\'';
    private static final char DOUBLE_QUOTE = '\"';
    private static final Pattern NEEDS_ESCAPE = Pattern.compile(
            "[\\\\'\"\\p{javaWhitespace}]"
    );

    static List<String> readTokens(String line) {
        if (line == null || (line = line.trim()).isEmpty()) {
            return Collections.emptyList();
        }
        final StringCharacterIterator iterator = new StringCharacterIterator(line);
        final ArrayList<String> ret = new ArrayList<>();

        final StringBuilder sb = new StringBuilder();
        char quoteChar = 0;  // 0 (no quote), singleQuote, doubleQuote

        for (; iterator.current() != StringCharacterIterator.DONE; iterator.next()) {
            char c = iterator.current();
            switch (c) {
                case SINGLE_QUOTE:
                case DOUBLE_QUOTE:
                    if (quoteChar == 0) {
                        quoteChar = c;  // begin quote
                    } else if (quoteChar == c) {
                        quoteChar = 0;  // end quote
                    }
                    break;
                case ESCAPE_CHAR:
                    char next = iterator.next();
                    if (next == StringCharacterIterator.DONE) {
                        // should not happen, but treat it as an escape char
                        sb.append(ESCAPE_CHAR);
                    } else {
                        // add next char, regardless what it is
                        sb.append(next);
                    }
                default:
                    if (Character.isWhitespace(c)) {
                        // stop here if quoteChar is 0 (no quote)
                        // check sb.notEmpty to avoid adding empty string
                        if (quoteChar == 0 && sb.length() != 0) {
                            ret.add(sb.toString());
                            sb.setLength(0);
                        }
                    } else {
                        // add next normal char
                        sb.append(c);
                    }
            }
        }
        if (sb.length() != 0) {
            // add remaining string if not empty
            ret.add(sb.toString());
        }
        return ret;
    }

    static void writeTokens(Iterable<? extends String> tokens, Appendable out) throws IOException {
        for (String token : tokens) {
            if (!NEEDS_ESCAPE.matcher(token).find()) {
                out.append(token);
            } else {
                out.append(DOUBLE_QUOTE).append(token).append(DOUBLE_QUOTE);
            }
            out.append('\t');    // tab separator
        }
        out.append('\n');       // new line
    }
}
