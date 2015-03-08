package bits.drawjav;

import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;


/**
 * @author Philip DeCamp
 */
public class CostItem extends AbstractRefable {


    public static final CostMetric<CostItem> METRIC = new CostMetric<CostItem>() {
        @Override
        public long costOf( CostItem obj ) {
            return obj.mCost;
        }
    };


    public final long mCost;


    public CostItem( long cost, ObjectPool<? super CostItem> pool ) {
        super( pool );
        mCost = cost;
    }


    @Override
    protected void freeObject() {}

}
