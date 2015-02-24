package bits.drawjav;

/**
 * @author Philip DeCamp
 */
public interface CostMetric<T> {
    public long costOf( T obj );

    public static CostMetric<Object> ONE = new CostMetric<Object>() {
        public long costOf( Object item ) {
            return 1;
        }
    };
}
