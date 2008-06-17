/* 
 *
 * Copyright (c) 2001 Torgeir Veimo
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

package com.nothome.delta;


/**
 * The output follows the GDIFF file specification available at
 * http://www.w3.org/TR/NOTE-gdiff-19970901.html.
 */

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class GDiffWriter implements DiffWriter {
    
    /**
     * Max length of a chunk.
     */
    public static final int CHUNK_SIZE = Short.MAX_VALUE;
    
    public static final byte EOF = 0;
    
    /**
     * Max length for single length data encode.
     */
    public static final int DATA_MAX = 246;
    
    public static final int DATA_USHORT = 247;
    public static final int DATA_INT = 248;
    public static final int COPY_USHORT_UBYTE = 249;
    public static final int COPY_USHORT_USHORT = 250;
    public static final int COPY_USHORT_INT = 251;
    public static final int COPY_INT_UBYTE = 252;
    public static final int COPY_INT_USHORT = 253;
    public static final int COPY_INT_INT = 254;
    public static final int COPY_LONG_INT = 255;

    private ByteArrayOutputStream buf = new ByteArrayOutputStream();

    protected boolean debug = false;
    
    private DataOutputStream output = null;
    
    public GDiffWriter(DataOutputStream os) throws IOException {
        this.output = os;
        // write magic string "d1 ff d1 ff 04"
        output.writeByte(0xd1);
        output.writeByte(0xff);
        output.writeByte(0xd1);
        output.writeByte(0xff);
        output.writeByte(0x04);
    }
    
    public void setDebug(boolean flag) { debug = flag; }
       
    public void addCopy(int offset, int length) throws IOException {
        writeBuf();
        
        //output debug data        
        if (debug)
            System.err.println("COPY off: " + offset + ", len: " + length);
        
        // output real data
        if (offset > Integer.MAX_VALUE) {
            // Actually, we don't support longer files than int.MAX_VALUE at the moment..
            output.writeByte(COPY_LONG_INT);
        } else if (offset < 65536)  {
            if (length < 256) {                
                output.writeByte(COPY_USHORT_UBYTE);
                output.writeShort(offset);
                output.writeByte(length);
            } else if (length > 65535) {
                output.writeByte(COPY_USHORT_INT);
                output.writeShort(offset);
                output.writeInt(length);
            } else {
                output.writeByte(COPY_USHORT_USHORT);
                output.writeShort(offset);
                output.writeShort(length);
            }
        } else {
            if (length < 256) {
                output.writeByte(COPY_INT_UBYTE);
                output.writeInt(offset);
                output.writeByte(length);
            } else if (length > 65535) {
                output.writeByte(COPY_INT_INT);
                output.writeInt(offset);
                output.writeInt(length);
            } else {
                output.writeByte(COPY_INT_USHORT);
                output.writeInt(offset);
                output.writeShort(length);
            }
        }
    }
    
    public void addData(byte[] b, int offset, int length) throws IOException {
        buf.write(b, offset, length);
        if (buf.size() >= CHUNK_SIZE)
            writeBuf();
    }
    
    private void writeBuf() throws IOException {
        if (buf.size() > 0) {
            if (buf.size() <= DATA_MAX) {
                output.writeByte(buf.size());
            } else if (buf.size() <= 65535) {
                output.writeByte(DATA_USHORT);
                output.writeShort(buf.size());
            } else {
                output.writeByte(DATA_INT);
                output.writeInt(buf.size());
            }
            buf.writeTo(output);
            buf.reset();
        }
    }
    
    public void flush() throws IOException 
    { 
		writeBuf(); 
    	output.flush(); 
    }
    
    public void close() throws IOException {
        this.flush();
        output.write((byte)EOF);
        output.close();
    }

}
