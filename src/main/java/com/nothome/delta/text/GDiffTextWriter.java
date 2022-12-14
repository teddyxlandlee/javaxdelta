/* 
 *
 * Copyright (c) 2008 Elias Ross
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.nothome.delta.text;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * A text-file format analog for GDIFF, which is only supported for binary
 * streams.
 * <p>
 * The output follows the following extended BNF format:
 *
 <pre>
 gdiff-text ::= header , { copy | data }
 header ::= 'gdt' , { version } , '\n'
 copy   ::= 'y' offset ',' length '\n'
 data   ::= 'i' length '\n' text-chunk '\n'
 length ::= hex-digit , { hex-digit }
 offset ::= hex-digit , { hex-digit }
 hex-digit  ::= '0'-'9' | 'a'-'f'
 text-chunk ::= (* arbitrary text string *)
 version ::= '1'-'9'
 </pre>
 * Note that 'y' is used for copy and 'i' for data since they aren't to be 
 * confused with the hex characters 'c' and 'd'.
 * <p>
 * Note that the length of text-string is capped at {@link #CHUNK_SIZE} characters
 * for this implementation.
 * <p>
 * The initial version is 1 and is optionally indicated. Newer versions may support 
 * additional commands and hints.
 * <p>
 * See also:
 * <a href="http://www.w3.org/TR/NOTE-gdiff-19970901.html">http://www.w3.org/TR/NOTE-gdiff-19970901.html</a>.
 */
public class GDiffTextWriter implements DiffTextWriter {

    /**
     * Line feed character.
     */
    public static final char LF = '\n';
    
    /**
     * Copy command character.
     */
    public static final char COPY = 'y';
    
    /**
     * Data command character.
     */
    public static final char DATA = 'i';
    
    /**
     * Comma delimiter.
     */
    public static final char COMMA = ',';

    static final String GDT = "gdt";
    
    private final CharArrayWriter caw = new CharArrayWriter();

    private final Writer w;
    
    /**
     * Max length of a "text-chunk".
     * Although this could be arbitrarily large, this caps
     * the buffer size, facilitating reading. 
     */
    public static final int CHUNK_SIZE = 32 * 1024;

    /**
     * Constructs a new GDiffTextWriter.
     */
    public GDiffTextWriter(Writer w) throws IOException {
        Objects.requireNonNull(w, "w");
        this.w = w;
        w.write(GDT);
        w.write(LF);
    }

    public void addCopy(int offset, int length) throws IOException {
        writeBuf();
        w.write(COPY);
        w.write(Integer.toHexString(offset));
        w.write(COMMA);
        w.write(Integer.toHexString(length));
        w.write(LF);
    }

    public void addData(char c) throws IOException {
        caw.append(c);
        if (caw.size() > CHUNK_SIZE)
            flush();
    }

    private void writeBuf() throws IOException {
        if (caw.size() == 0)
            return;
        w.write(DATA);
        int i = caw.size();
        w.write(Integer.toHexString(i));
        w.write(LF);
        caw.writeTo(w);
        caw.reset();
        w.write(LF);
    }

    public void flush() throws IOException {
        writeBuf();
        w.flush();
    }

    public void close() throws IOException {
        flush();
        w.close();
    }

}
