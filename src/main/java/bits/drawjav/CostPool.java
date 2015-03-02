package bits.drawjav;

import bits.util.ref.ObjectPool;
import bits.util.ref.Refable;

import java.nio.channels.Channel;
import java.util.*;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class CostPool<T extends Refable> implements ObjectPool<T>, Channel {

    private static final Logger sLog = Logger.getLogger( CostPool.class.getName() );

    private final CostMetric<? super T> mMetric;
    private final Stack<T> mPool = new Stack<T>();

    private boolean mOpen = true;

    private long mCostCap         = -1;  // Max allowed cost of items in pool before items are disposed.
    private long mPoolCost        =  0;  // Current cost of items in pool.
    private long mOutstandingCost =  0;  // Current cost of items outside pool.
    private long mWarningCost     = -1;  // When outstanding cost gets this high, issue warning to user.

    private int mRawAllocCount = 0;

    private int mDisposing = 0;


    /**
     * @param costCap        The allowed combined cost of all items before pool begins disposing items. -1 if none.
     * @param warningThresh  If the cost of outstanding objects items reaches this threshold, the
     *                       RefPool will issue a warning to the logger. -1 if no warning.
     * @param optMetric      Metric used to determine cost of each item. If {@code optMetric == null}, then each
     *                       item will be assigned a cost of 1.
     */
    public CostPool( long costCap, long warningThresh, CostMetric<? super T> optMetric ) {
        mCostCap = costCap;
        if( optMetric == null ) {
            mMetric = CostMetric.ONE;
        } else {
            mMetric = optMetric;
        }
        mWarningCost = warningThresh;
    }


    /**
     * User should call this method every time a pooled object is allocated if the user wants to track the
     * items outstanding from the pool.
     *
     * @param item
     */
    public synchronized void allocated( T item ) {
        if( !mOpen ) {
            return;
        }

        long cost = mMetric.costOf( item );
        mOutstandingCost += cost;
        if( mWarningCost >= 0 && mOutstandingCost >= mWarningCost ) {
            mWarningCost = -1;
            sLog.warning( "Detected unusually high allocation rate of pooled objects. There might be a memory leak." );
        }
    }

    @Override
    public synchronized T poll() {
        if( !mOpen ) {
            return null;
        }

        switch( mPool.size() ) {
        case 0:
            return null;
        case 1:
            mPoolCost = 0;
            return mPool.pop();
        default:
            T item = mPool.pop();
            mPoolCost -= mMetric.costOf( item );
            return item;
        }
    }

    @Override
    public synchronized boolean offer( T item ) {
        if( mDisposing > 0 ) {
            return false;
        }

        long cost = mMetric.costOf( item );
        mOutstandingCost -= cost;
        if( mOutstandingCost < 0 ) {
            mOutstandingCost = 0;
        }

        // Check if there is room in pool.
        if( mCostCap >= 0 && mPoolCost >= mCostCap ) {
            return false;
        }

        mPool.push( item );
        mPoolCost += cost;
        return true;
    }

    /**
     * Must be called by user to dispose of object that should not return to pool.
     * @param item
     */
    public void dispose( T item ) {
        try {
            synchronized( this ) {
                mDisposing++;
                mOutstandingCost -= mMetric.costOf( item );
                if( mOutstandingCost < 0 ) {
                    mOutstandingCost = 0;
                }
            }

            item.deref();

        } finally {
            synchronized( this ) {
                mDisposing--;
            }
        }
    }

    /**
     * Disposes all items currently in the pool.
     */
    public void clear() {
        doClear( false );
    }

    @Override
    public void close() {
        doClear( true );
    }

    @Override
    public boolean isOpen() {
        return mOpen;
    }



    private void doClear( boolean closing ) {
        List<T> list;

        try {
            synchronized( this ) {
                if( closing ) {
                    if( !mOpen ) {
                        return;
                    }
                    mOpen = false;
                }

                list = new ArrayList<T>( mPool );
                mPool.clear();
                mPoolCost = 0;
                mDisposing++;
            }

            for( T item : list ) {
                item.deref();
            }
        } finally {
            // If closing, leave mDisposing at 1.
            if( !closing ) {
                synchronized( this ) {
                    mDisposing--;
                }
            }
        }
    }


}
