package bits.drawjav.pipe;

/**
 * @author Philip DeCamp
 */
public interface FilterPad<T> {
    boolean blocking();
    int available();
}
