package xland.ioutils.xdelta.wrapper;

import com.nothome.delta.ByteBufferSeekableSource;
import com.nothome.delta.SeekableSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class SyncPoolOutputStream extends OutputStream {
    private byte[] buf;
    private int size;
    private ExtraLarge extraDelegate;

    public SyncPoolOutputStream(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("Initial capacity must be non-negative");
        buf = new byte[initialCapacity];
    }

    public void ensureCapacity(int num) {
        if (num <= 0) return;
        if (size > buf.length - num) {  // sum + num > buf.length, but avoid overflow
            if (size + num < 0 || size + num > EXTRA_BUF_THRESHOLD) {   // overflow, copy to extraDelegate
                if (extraDelegate == null) extraDelegate = new ExtraLarge();
                extraDelegate.ensureCapacity((long)size + (long)num);
                extraDelegate.write(buf, 0, size);
            } else {
                byte[] oldBuf = buf;
                buf = new byte[size + num];
                System.arraycopy(oldBuf, 0, buf, 0, size);
            }
        }
    }

    public InputStream makeInputStream() {
        if (extraDelegate == null) {
            return new ByteArrayInputStream(buf, 0, size);
        } else {
            return extraDelegate.makeInputStream();
        }
    }

    public SeekableSource makeSeekableSource() {
        if (extraDelegate == null) {
            return new ByteBufferSeekableSource(ByteBuffer.wrap(buf, 0, size));
        } else {
            return extraDelegate.makeSeekableSource();
        }
    }

    @Override
    public void write(int b) {
        ensureCapacity(1);
        if (extraDelegate == null) {
            buf[size++] = (byte)b;
        } else {
            extraDelegate.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkIndexBounds(b, off, len);
        ensureCapacity(len);
        if (extraDelegate == null) {
            System.arraycopy(b, off, buf, size, len);
            size += len;
        } else {
            extraDelegate.write(b, off, len);
        }
    }

    @Override
    public void close() {
        buf = null;
        if (extraDelegate != null) extraDelegate.close();
    }

    private long size() {
        return extraDelegate == null ? size : extraDelegate.lastCursor;
    }

    public boolean contentEquals(SyncPoolOutputStream other) {
        if (other == null) return false;
        if (size() != other.size()) return false;
        InputStream thisStream = this.makeInputStream();
        InputStream otherStream = other.makeInputStream();
        while (true) {
            int b1, b2;
            try {
                b1 = thisStream.read();
                b2 = otherStream.read();
            } catch (IOException e) {
                throw new IncompatibleClassChangeError("SyncPoolOutputStream.makeInputStream().read() should not throw IOException");
            }
            if (b1 == -1) break;
            if (b1 != b2) return false;
        }
        return true;
    }

    private static void checkIndexBounds(byte[] b, int off, int len) {
        if ((off | len) < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
    }

    private static final int EXTRA_BUF_THRESHOLD = 0x4000_0000;
    private static final int EXTRA_BUF_MASK = EXTRA_BUF_THRESHOLD - 1;
    private static final int EXTRA_BUF_THRESHOLD_BYTES = 30;

    private static class ExtraLarge extends OutputStream {
        private byte[][] extraBuf;
        private long lastCursor;

        ExtraLarge() {
            extraBuf = new byte[][]{new byte[EXTRA_BUF_THRESHOLD]};
        }

        void ensureCapacity(long num) {
            if (num <= 0L) return;
            if ((lastCursor & EXTRA_BUF_MASK) + num > EXTRA_BUF_THRESHOLD) {
                byte[][] oldExtraBuf = extraBuf;
                extraBuf = new byte[oldExtraBuf.length + 1][];
                System.arraycopy(oldExtraBuf, 0, extraBuf, 0, oldExtraBuf.length);
                extraBuf[oldExtraBuf.length] = new byte[EXTRA_BUF_THRESHOLD];
            }
        }

        @Override
        public void write(int b) {
            ensureCapacity(1);
            int which = (int)lastCursor >>> EXTRA_BUF_THRESHOLD_BYTES;
            int where = (int) (lastCursor & EXTRA_BUF_MASK);
            extraBuf[which][where] = (byte)b;
            lastCursor++;
        }

        @Override
        public void write(byte[] b) {
            this.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            ensureCapacity(len);
            int overflow = ((int)lastCursor & EXTRA_BUF_MASK) + len - EXTRA_BUF_THRESHOLD;
            if (overflow > 0) {
                // write into two parts
                writeInternal(b, off, len - overflow);
                writeInternal(b, len - overflow + off, overflow);
            } else {
                writeInternal(b, off, len);
            }
        }

        private void writeInternal(byte[] b, int off, int len) {
            int which = (int)lastCursor >>> EXTRA_BUF_THRESHOLD_BYTES;
            int where = (int) (lastCursor & EXTRA_BUF_MASK);
            System.arraycopy(b, off, extraBuf[which], where, len);
            lastCursor += len;
        }

        @Override
        public void close() {
            Arrays.fill(extraBuf, null);
        }

        InputStream makeInputStream() {
            final byte[][] extraBuf = this.extraBuf;
            final long size = this.lastCursor;
            return new InputStream() {
                long currentCursor;

                @Override
                public int read() {
                    if (currentCursor >= size) return -1;   // currentCursor + 1 > size
                    int which = (int)currentCursor >>> EXTRA_BUF_THRESHOLD_BYTES;
                    int where = (int) (currentCursor & EXTRA_BUF_MASK);
                    currentCursor++;
                    return extraBuf[which][where] & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    checkIndexBounds(b, off, len);
                    if (len == 0) return 0;
                    if (currentCursor >= size) return -1;
                    if (len > size - currentCursor) {
                        len = (int)(size - currentCursor);
                    }

                    int overflow = ((int)currentCursor & EXTRA_BUF_MASK) + len - EXTRA_BUF_THRESHOLD;
                    if (overflow > 0) {
                        // read into two parts
                        readInternal(b, off, len - overflow);
                        readInternal(b, len - overflow + off, overflow);
                    } else {
                        readInternal(b, off, len);
                    }
                    return len;
                }

                private void readInternal(byte[] b, int off, int len) {
                    int which = (int)currentCursor >>> EXTRA_BUF_THRESHOLD_BYTES;
                    int where = (int) (currentCursor & EXTRA_BUF_MASK);
                    System.arraycopy(extraBuf[which], where, b, off, len);
                    currentCursor += len;
                }
            };
        }

        SeekableSource makeSeekableSource() {
            final byte[][] extraBuf = this.extraBuf;
            final long size = this.lastCursor;
            return new SeekableSource() {
                long currentCursor;

                @Override
                public void seek(long pos) throws IOException {
                    if (currentCursor > size - pos) {
                        throw new IOException("Cannot seek through " + pos + " bytes because of overflow");
                    }
                    currentCursor += pos;
                }

                @Override
                public int read(ByteBuffer bb) {
                    if (currentCursor >= size) return -1;
                    int len = bb.limit();

                    if (len > size - currentCursor) {
                        len = (int)(size - currentCursor);
                    }

                    int overflow = ((int)currentCursor & EXTRA_BUF_MASK) + len - EXTRA_BUF_THRESHOLD;
                    if (overflow > 0) {
                        // read into two parts
                        readInternal(bb, len - overflow);
                        readInternal(bb, overflow);
                    } else {
                        readInternal(bb, len);
                    }
                    return len;
                }

                private void readInternal(ByteBuffer bb, int len) {
                    int which = (int)currentCursor >>> EXTRA_BUF_THRESHOLD_BYTES;
                    int where = (int) (currentCursor & EXTRA_BUF_MASK);
                    bb.put(extraBuf[which], where, len);
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
