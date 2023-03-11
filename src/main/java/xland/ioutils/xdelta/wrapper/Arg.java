package xland.ioutils.xdelta.wrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Arg {
    //UNIX = 1, RAW = 0, GNU = -1;
    private final int type;
    private final String ctx;

    private Arg(int type, String ctx) {
        this.type = type;
        this.ctx = ctx;
    }

    static Arg unix(char c) {
        return new Arg(1, String.valueOf(c));
    }

    static Arg gnu(String s) {
        return new Arg(-1, s);
    }

    static Arg raw(String s) {
        return new Arg(0, s);
    }

    String getContext() {
        return ctx;
    }

    int getType() {
        return Integer.signum(type);
    }

    boolean isGnu() {
        return type < 0;
    }

    boolean isUnix() {
        return type > 0;
    }

    boolean isRaw() {
        return type == 0;
    }

    public String toString() {
        if (isGnu()) return "--" + ctx;
        else if (isRaw()) return ctx;
        return '-' + ctx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Arg)) return false;
        final Arg other = (Arg) o;
        return Objects.equals(this.ctx, other.ctx) &&
                this.getType() == other.getType();
    }

    @Override
    public int hashCode() {
        return (Objects.hashCode(this.ctx) << 2) | (getType() & 3);
    }

    static List<Arg> parse(String... args) {
        List<Arg> list = new ArrayList<>();
        for (String s : args) {
            if ("--".equals(s) || s.length() < 2 || s.charAt(0) != '-')
                list.add(raw(s));
            else if (s.charAt(1) == '-') {
                int idx;
                if ((idx = s.indexOf('=')) < 0)
                    list.add(gnu(s.substring(2)));
                else {
                    list.add(gnu(s.substring(2, idx)));
                    list.add(raw(s.substring(idx + 1)));
                }
            } else {
                final char[] chars = s.toCharArray();
                for (int i = 1; i < chars.length; i++)
                    list.add(unix(chars[i]));
            }
        }
        return list;
    }

    static List<Arg> parse(String[] args, Function<Character, String> unixMapper) {
        return parse(args).stream().flatMap(arg -> {
            if (arg.isUnix()) {
                final String s = unixMapper.apply(arg.getContext().charAt(0));
                if (s == null) return Stream.empty();
                return Stream.of(gnu(s));
            }
            return Stream.of(arg);
        }).collect(Collectors.toList());
    }
}
