package bits.drawjav.pipe;

/**
 * @author Philip DeCamp
 */
public enum FilterErr {
    NONE,
    DONE,
    TIMEOUT,
    EXCEPTION,

    @Deprecated UNDERFLOW,
    @Deprecated OVERFLOW
}
