package io.netty.buffer;

import io.netty.util.CharsetUtil;

/**
 * NOTE: this is a copy of a recent utility method in Netty, the RIGHT thing to do is to remove this
 * code and use a later version of Netty which has this method on ByteBufUtil.
 */
public class ByteBufUtilTemp {

    private static final byte WRITE_UTF_UNKNOWN = (byte) '?';
    private static final int MAX_BYTES_PER_CHAR_UTF8 = 3;

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write
     * it to a {@link ByteBuf}.
     *
     * This method returns the actual number of bytes written.
     */
    public static int writeUtf8(ByteBuf buf, CharSequence seq) {
        final int len = seq.length();
        buf.ensureWritable(len * MAX_BYTES_PER_CHAR_UTF8);

        for (;;) {
            if (buf instanceof AbstractByteBuf) {
                return writeUtf8((AbstractByteBuf) buf, seq, len);
            } else if (buf instanceof WrappedByteBuf) {
                // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                buf = buf.unwrap();
            } else {
                byte[] bytes = seq.toString().getBytes(CharsetUtil.UTF_8);
                buf.writeBytes(bytes);
                return bytes.length;
            }
        }
    }

    // Fast-Path implementation
    private static int writeUtf8(AbstractByteBuf buffer, CharSequence seq, int len) {
        int oldWriterIndex = buffer.writerIndex;
        int writerIndex = oldWriterIndex;

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        for (int i = 0; i < len; i++) {
            char c = seq.charAt(i);
            if (c < 0x80) {
                buffer._setByte(writerIndex++, (byte) c);
            } else if (c < 0x800) {
                buffer._setByte(writerIndex++, (byte) (0xc0 | (c >> 6)));
                buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
                    continue;
                }
                final char c2;
                try {
                    // Surrogate Pair consumes 2 characters. Optimistically try to get the next character to avoid
                    // duplicate bounds checking with charAt. If an IndexOutOfBoundsException is thrown we will
                    // re-throw a more informative exception describing the problem.
                    c2 = seq.charAt(++i);
                } catch (IndexOutOfBoundsException e) {
                    buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
                    break;
                }
                if (!Character.isLowSurrogate(c2)) {
                    buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
                    buffer._setByte(writerIndex++, Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2);
                    continue;
                }
                int codePoint = Character.toCodePoint(c, c2);
                // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                buffer._setByte(writerIndex++, (byte) (0xf0 | (codePoint >> 18)));
                buffer._setByte(writerIndex++, (byte) (0x80 | ((codePoint >> 12) & 0x3f)));
                buffer._setByte(writerIndex++, (byte) (0x80 | ((codePoint >> 6) & 0x3f)));
                buffer._setByte(writerIndex++, (byte) (0x80 | (codePoint & 0x3f)));
            } else {
                buffer._setByte(writerIndex++, (byte) (0xe0 | (c >> 12)));
                buffer._setByte(writerIndex++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
            }
        }
        // update the writerIndex without any extra checks for performance reasons
        buffer.writerIndex = writerIndex;
        return writerIndex - oldWriterIndex;
    }

    /**
     * Determine if {@code c} lies within the range of values defined for
     * <a href="http://unicode.org/glossary/#surrogate_code_point">Surrogate Code Point</a>.
     * @param c the character to check.
     * @return {@code true} if {@code c} lies within the range of values defined for
     * <a href="http://unicode.org/glossary/#surrogate_code_point">Surrogate Code Point</a>. {@code false} otherwise.
     */
    public static boolean isSurrogate(char c) {
        return c >= '\uD800' && c <= '\uDFFF';
    }

}
