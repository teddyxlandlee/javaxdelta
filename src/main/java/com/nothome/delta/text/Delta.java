 /*
  *
  * Copyright (c) 2001 Torgeir Veimo
  * Copyright (c) 2002 Nicolas PERIDONT
  * Bug Fixes: Daniel Morrione dan@morrione.net
  * Copyright (c) 2006 Heiko Klein
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
  * Change Log:
  * iiimmddyyn  nnnnn  Description
  * ----------  -----  -------------------------------------------------------
  * gls100603a         Fixes from Torgeir Veimo and Dan Morrione
  * gls110603a         Stream not being closed thus preventing a file from
  *                       being subsequently deleted.
  * gls031504a         Error being written to stderr rather than throwing exception
  */

package com.nothome.delta.text;

 import java.io.*;
 import java.nio.CharBuffer;
 import java.nio.file.Files;
 import java.nio.file.Paths;
 import java.util.Objects;

 /**
 * Class for computing deltas against a source.
 * The source file is read by blocks and a hash is computed per block.
 * Then the target is scanned for matching blocks.
 * <p/>
 * Essentially a duplicate of com.nothome.delta.Delta for character streams.
 */
public class Delta {

    /**
     * Default size of 16.
     * For "Lorem ipsum" files, the ideal size is about 14. Any smaller and
     * the patch size becomes actually be larger.
     * <p>
     * Use a size like 64 or 128 for large files.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1<<4;
    
    /**
     * Chunk Size.
     */
    private int S;

    private TargetState target;
    private DiffTextWriter output;
    
    public Delta() {
        setChunkSize(DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * Sets the chunk size used.
     * Larger chunks are faster and use less memory, but create larger patches
     * as well.
     *
     */
    public void setChunkSize(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Invalid size");
        S = size;
    }
    
    /**
     * Compares the source bytes with target bytes, writing to output.
     */
    public void compute(CharSequence source, CharSequence target, Writer output)
    throws IOException {
        compute(new CharBufferSeekableSource(source), 
                new StringReader(target.toString()),
                new GDiffTextWriter(output));
    }
    
    /**
     * Compares the source bytes with target bytes, returning differences.
     */
    public String compute(CharSequence source, CharSequence target)
    throws IOException {
        StringWriter sw = new StringWriter();
        compute(source, target, sw);
        return sw.toString();
    }
    
    /**
     * Compares the source with a target, writing to output.
     * 
     * @param targetIS second file to compare with
     * @param output diff output
     * 
     * @throws IOException if diff generation fails
     */
    public void compute(SeekableSource seekSource, Reader targetIS, DiffTextWriter output)
    throws IOException {

        SourceState source = new SourceState(seekSource);
        target = new TargetState(targetIS);
        this.output = output;

        while (!target.eof()) {
            int index = target.find(source);
            if (index != -1) {
                int offset = index * S;
                source.seek(offset);
                int match = target.longestMatch(source);
                if (match >= S) {
                    output.addCopy(offset, match);
                } else {
                    // move the position back according to how much we can't copy
                    target.tbuf.position(target.tbuf.position() - match);
                    addData();
                }
            } else {
                addData();
            }
        }
        output.close();
    }
    
    private void addData() throws IOException {
        int i = target.read();
        if (i == -1)
            return;
        output.addData((char)i);
    }
    
    class SourceState {

        private final Checksum checksum;
        private final SeekableSource source;
        
        public SourceState(SeekableSource source) throws IOException {
            checksum = new Checksum(source, S);
            this.source = source;
            source.seek(0);
        }

        public void seek(long index) throws IOException {
            source.seek(index);
        }

        /**
         * Returns a debug <code>String</code>.
         */
        @Override
        public String toString()
        {
            return "Source"+
                " checksum=" + this.checksum +
                " source=" + this.source +
                "";
        }
        
    }
        
    class TargetState {
        
        private final Readable c;
        private final CharBuffer tbuf = CharBuffer.allocate(blocksize());
        private final CharBuffer sbuf = CharBuffer.allocate(blocksize());
        private long hash;
        private boolean hashReset = true;
        private boolean eof;
        
        TargetState(Reader targetIS) {
            c = targetIS;
            tbuf.limit(0);
        }
        
        private int blocksize() {
            return Math.max(1024 * 8, S * 4);
        }

        /**
         * Returns the index of the next N bytes of the stream.
         */
        public int find(SourceState source) throws IOException {
            if (eof)
                return -1;
            sbuf.clear();
            sbuf.limit(0);
            if (hashReset) {
                while (tbuf.remaining() < S) {
                    tbuf.compact();
                    int read = c.read(tbuf);
                    tbuf.flip();
                    if (read == -1) {
                        return -1;
                    }
                }
                hash = Checksum.queryChecksum(tbuf, S);
                hashReset = false;
            }
            return source.checksum.findChecksumIndex(hash);
        }

        public boolean eof() {
            return eof;
        }

        /**
         * Reads a char.
         */
        public int read() throws IOException {
            if (tbuf.remaining() <= S) {
                readMore();
            }
            if (!tbuf.hasRemaining()) {
                eof = true;
                return -1;
            }
            char b = tbuf.get();
            if (tbuf.remaining() >= S) {
                char nchar = tbuf.get( tbuf.position() + S -1 );
                hash = Checksum.incrementChecksum(hash, b, nchar, S);
            }
            return b;
        }

        /**
         * Returns the longest match length at the source location.
         */
        public int longestMatch(SourceState source) throws IOException {
            int match = 0;
            hashReset = true;
            while (true) {
                if (!sbuf.hasRemaining()) {
                    sbuf.clear();
                    int read = source.source.read(sbuf);
                    sbuf.flip();
                    if (read == -1)
                        return match;
                }
                if (!tbuf.hasRemaining()) {
                    readMore();
                    if (!tbuf.hasRemaining()) {
                        eof = true;
                        return match;
                    }
                }
                if (sbuf.get() != tbuf.get()) {
                    tbuf.position(tbuf.position() - 1);
                    return match;
                }
                match++;
            }
        }

        private void readMore() throws IOException {
            tbuf.compact();
            c.read(tbuf);
            tbuf.flip();
        }

        /**
         * Returns a debug <code>String</code>.
         */
        @Override
        public String toString()
        {
            return "Target[" +
                " targetBuff=" + dump() + // this.tbuf +
                " sourceBuff=" + this.sbuf +
                " hashf=" + this.hash +
                " eof=" + this.eof +
                "]";
        }
        
        private String dump() { return dump(tbuf); }
        
        private String dump(CharBuffer bb) {
            bb.mark();
            StringBuilder sb = new StringBuilder();
            while (bb.hasRemaining())
                sb.append(bb.get());
            bb.reset();
            return sb.toString();
        }
        
    }

     private static void transferTo(Reader reader, Writer out) throws IOException {
         Objects.requireNonNull(out, "out");
         char[] buffer = new char[8192];
         int nRead;
         while ((nRead = reader.read(buffer, 0, 8192)) >= 0) {
             out.write(buffer, 0, nRead);
         }
     }
    
    static CharSequence toString(Reader r) throws IOException {
        StringWriter writer = new StringWriter();
        transferTo(r, writer);
        return writer.toString();
    }
    
    /**
     * Creates a patch with file names.
     */
    public static void main(String[] s) throws IOException {
        if (s.length != 2) {
            System.err.println("Usage: java ...Delta file1 file2 [> somefile]");
            return;
        }
        Reader r1 = Files.newBufferedReader(Paths.get(s[0]));
        Reader r2 = Files.newBufferedReader(Paths.get(s[1]));
        CharSequence sb = toString(r1);
        Delta d = new Delta();
        OutputStreamWriter osw = new OutputStreamWriter(System.out);
        d.compute(new CharBufferSeekableSource(sb), r2, new GDiffTextWriter(osw));
        osw.close();
    }
    
}
