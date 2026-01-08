package xland.ioutils.xdelta.wrapper;

public class NameAllocator {
    private final CharSequence prefix;
    private final CharSequence suffix;
    private int counter;

    public NameAllocator(CharSequence prefix, CharSequence suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String nextName() {
        return "" + prefix + ++counter + suffix;
    }
}
