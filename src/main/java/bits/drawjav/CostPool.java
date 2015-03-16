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
    private final Stack<T> sPool = new Stack<T>();

    private boolean sOpen = true;

    private long sCostCap       = -1; // Max allowed cost of items in pool before items are disposed.
    private long sPoolCost      = 0;  // Current cost of items in pool.
    private long sAllocatedCost = 0;  // Current cost of items outside pool.

    private long    sWarningThresh = -1; // When ollocated cost gets this high, issue warning to user.
    private boolean sHasWarned     = false;


    private int sDisposing = 0;


    /**
     * @param costCap        The allowed combined cost of all items before pool begins disposing items. -1 if none.
     * @param warningThresh  If the cost of allocated objects items reaches this threshold, the
     *                       RefPool will issue a warning to the logger. -1 if no warning.
     * @param optMetric      Metric used to determine cost of each item. If {@code optMetric == null}, then each
     *                       item will be assigned a cost of 1.
     */
    public CostPool( long costCap, long warningThresh, CostMetric<? super T> optMetric ) {
        sCostCap = costCap;
        if( optMetric == null ) {
            mMetric = CostMetric.ONE;
        } else {
            mMetric = optMetric;
        }
        sWarningThresh = warningThresh;
    }


    /**
     * User should call this method every time a pooled object is allocated if the user wants to track the
     * item allocations.
     *
     * @param item
     */
    public synchronized void allocated( T item ) {
        if( !sOpen ) {
            return;
        }

        long cost = mMetric.costOf( item );
        sAllocatedCost += cost;
        if( sAllocatedCost >= sWarningThresh && !sHasWarned && sWarningThresh >= 0 ) {
            sWarningThresh = -1;
            sLog.warning( "Detected unusually high allocation rate of pooled objects. There might be a memory leak." );
        }
    }

    @Override
    public synchronized T poll() {
        if( !sOpen ) {
            return null;
        }

        switch( sPool.size() ) {
        case 0:
            return null;
        case 1:
            sPoolCost = 0;
            return sPool.pop();
        default:
            T item = sPool.pop();
            sPoolCost -= mMetric.costOf( item );
            return item;
        }
    }

    @Override
    public synchronized boolean offer( T item ) {
        long cost = mMetric.costOf( item );

        // Check if there is room in pool.
        if( sDisposing > 0 || 0 <= sCostCap && sCostCap <= sPoolCost ) {
            sAllocatedCost -= cost;
            if( sAllocatedCost < sPoolCost ) {
                sAllocatedCost = sPoolCost;
            }
            return false;
        }

        sPool.push( item );
        sPoolCost += cost;
        return true;
    }

    /**
     * Must be called by user to vDispose of object that should not return to pool.
     * @param item
     */
    public void dispose( T item ) {
        try {
            synchronized( this ) {
                sDisposing++;
            }
            item.deref();
        } finally {
            synchronized( this ) {
                sDisposing--;
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
        return sOpen;
    }



    long poolCost() {
        return sPoolCost;
    }


    long allocatedCost() {
        return sAllocatedCost;
    }



    boolean hasWarned() {
        return sHasWarned;
    }


    long warningThresh() {
        return sWarningThresh;
    }




    private void doClear( boolean closing ) {
        List<T> derefList;

        try {
            synchronized( this ) {
                if( closing ) {
                    if( !sOpen ) {
                        return;
                    }
                    sOpen = false;
                }

                derefList = new ArrayList<T>( sPool );
                sPool.clear();
                sPoolCost = 0;
                sDisposing++;
            }

            for( T item : derefList ) {
                item.deref();
            }
        } finally {
            // If closing, leave sDisposing at 1.
            if( !closing ) {
                synchronized( this ) {
                    sDisposing--;
                }
            }
        }
    }

}
