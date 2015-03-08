package bits.drawjav;

import bits.util.ref.ObjectPool;
import bits.util.ref.Refable;

import java.nio.channels.Channel;
import java.util.*;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class MultiCostPool<T extends Refable> implements Channel {

    private static final Logger sLog = Logger.getLogger( MultiCostPool.class.getName() );


    private final Object mLock = this;
    private final CostMetric<? super T> mMetric;
    private final LinkedHashMap<Object, Pool> sPools = new LinkedHashMap<Object, Pool>( 12, 0.75f, false );


    private final int mMaxEmptyAge = 10; // Max times a pool may be empty during poll before being disposed.

    private long    sCostCap     = -1;  // Max allowed cost of items in pool before items are disposed.
    private long    sWarningCost = -1;  // When outstanding cost gets this high, issue warning to user.
    private boolean sHasWarned   = false;


    private long sPoolCostTotal  = 0;  // Current cost of items in pool.
    private long sAllocatedTotal = 0;  // Current cost of items outside pool.

    private int     sDisposing = 0;
    private boolean sOpen      = true;


    /**
     * @param costCap        The allowed combined cost of all items before pool begins disposing items. -1 if none.
     * @param warningThresh  If the cost of outstanding objects items reaches this threshold, the
     *                       RefPool will issue a warning to the logger. -1 if no warning.
     * @param optMetric      Metric used to determine cost of each item. If {@code optMetric == null}, then each
     *                       item will be assigned a cost of 1.
     */
    public MultiCostPool( long costCap, long warningThresh, CostMetric<? super T> optMetric ) {
        sCostCap = costCap;
        if( optMetric == null ) {
            mMetric = CostMetric.ONE;
        } else {
            mMetric = optMetric;
        }
        sWarningCost = warningThresh;
    }


    /**
     * @param key Item type key.
     * @return view of the multipool for this key. <br>
     *         Calling {@code this.pool( key ).poll()} or {@code this.pool( key ).offer( item )} are equivalent to
     *         {@code this.poll( key )} or {@code this.offer( key, item )}, respectively.
     *
     */
    public ObjectPool<T> pool( Object key ) {
        return new View( key );
    }

    /**
     * @param key Item type key used to sort item pools.
     * @return Available item from associated pool, or {@code null} if none.
     */
    public T poll( Object key ) {
        synchronized( mLock ) {
            Pool pool = sPools.remove( key );
            if( pool == null ) {
                return null;
            }
            sPools.put( key, pool );
            return pool.sPoll();
        }
    }

    /**
     * @param key  Item type key used to sort item pools.
     * @param item Item to offer to pool.
     * @return true iff item was accepted
     */
    public boolean offer( Object key, T item ) {
        List<T> derefList;
        boolean ret;

        long itemCost = mMetric.costOf( item );

        synchronized( mLock ) {
            // Check if there's any room.
            if( sDisposing > 0 ) {
                sAllocatedTotal -= itemCost;
                if( sAllocatedTotal < sPoolCostTotal ) {
                    sAllocatedTotal = sPoolCostTotal;
                }
                return false;
            }

            // Get pool from map. Note that pool may be null.
            Pool pool = sPools.get( key );

            // Check if there's enough room.
            if( sCostCap < 0 || sPoolCostTotal < sCostCap ) {
                if( pool == null ) {
                    pool = new Pool();
                    sPools.put( key, pool );
                }
                pool.sPush( item, itemCost );
                return true;
            }


            // Need to clear space.
            // Note that pool may still be null.
            derefList = new ArrayList<T>( 2 );
            sDisposing++;
            long spaceNeeded = sPoolCostTotal - sCostCap + 1;
            long spaceCleared = 0;
            Iterator<Pool> iter = sPools.values().iterator();

            while( spaceCleared < spaceNeeded && iter.hasNext() ) {
                Pool p = iter.next();
                spaceCleared += p.sPollMultiple( spaceNeeded - spaceCleared, derefList );

                // If a pool lies empty for too long, remove it.
                if( p.sUpdateEmptyAge() > mMaxEmptyAge ) {
                    iter.remove();
                }
            }

            ret = spaceCleared >= spaceNeeded;
            if( ret ) {
                if( pool == null ) {
                    pool = new Pool();
                    sPools.put( key, pool );
                }
                pool.sPush( item, itemCost );
            }
        }

        try {
            for( T deadItem : derefList ) {
                deadItem.deref();
            }
        } finally {
            synchronized( mLock ) {
                sDisposing--;
            }
        }

        return ret;
    }

    /**
     * User should call this method every time a pooled object is allocated if the user wants to track the
     * items outstanding from the pool.
     *
     * @param item Item that was allocated.
     */
    public void allocated( T item ) {
        synchronized( mLock ) {
            if( !sOpen ) {
                return;
            }

            long cost = mMetric.costOf( item );
            sAllocatedTotal += cost;
            if( sAllocatedTotal >= sWarningCost && !sHasWarned && sWarningCost >= 0 ) {
                sHasWarned = true;
                sLog.warning( "Detected unusually high allocation rate of pooled objects. There might be a memory leak." );
            }
        }
    }

    /**
     * Must be called by user to dispose of object that should not return to pool.
     *
     * @param item Item to dispose.
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

    /**
     * Disposes all items currently in pools and closes pools so they can no longer be used.
     */
    @Override
    public void close() {
        doClear( true );
    }

    @Override
    public boolean isOpen() {
        return sOpen;
    }



    boolean hasWarned() {
        return sHasWarned;
    }


    long warningThresh() {
        return sWarningCost;
    }


    long poolCost() {
        return sPoolCostTotal;
    }


    long poolCost( Object key ) {
        synchronized( mLock ) {
            Pool pool = sPools.get( key );
            return pool == null ? 0 : pool.sPoolCost;
        }
    }


    long allocatedCost() {
        return sAllocatedTotal;
    }




    private void doClear( boolean closing ) {
        List<Pool> pools;
        List<T> list = new ArrayList<T>();

        try {
            synchronized( mLock ) {
                if( closing ) {
                    if( !sOpen ) {
                        return;
                    }
                    sOpen = false;
                }

                pools = new ArrayList<Pool>( sPools.values() );
                sPools.clear();
                sDisposing++;
            }

            for( Pool pool: pools ) {
                synchronized( mLock ) {
                    pool.sClear( list );
                }

                for( T item: list ) {
                    item.deref();
                }

                list.clear();
            }

        } finally {
            synchronized( this ) {
                if( !closing ) {
                    // If closing, leave mDisposing at 1.
                    sDisposing--;
                }
            }
        }
    }


    private class Pool {

        private Stack<T> sPool     = new Stack<T>();
        private long     sPoolCost = 0;
        private int      sEmptyAge = 0;

        void sPush( T item, long cost ) {
            sPool.push( item );
            sPoolCost += cost;
            sPoolCostTotal += cost;
        }


        T sPoll() {
            switch( sPool.size() ) {
            case 0:
                return null;

            case 1:
                sPoolCostTotal -= sPoolCost;
                sPoolCost = 0;
                return sPool.pop();

            default:
                T item = sPool.pop();
                long cost = mMetric.costOf( item );
                sPoolCost -= cost;
                sPoolCostTotal -= cost;
                return item;
            }
        }


        long sPollMultiple( long cost, List<T> out ) {
            long ret = 0;
            while( ret < cost && !sPool.isEmpty() ) {
                T item = sPool.pop();
                ret += mMetric.costOf( item );
                out.add( item );
            }

            ret = Math.min( ret, sPoolCost );
            sPoolCost -= ret;
            sPoolCostTotal -= ret;
            return ret;
        }


        void sClear( List<T> derefList ) {
            derefList.addAll( sPool );
            sPool.clear();
            sPoolCostTotal -= sPoolCost;
            sPoolCost = 0;
        }


        int sUpdateEmptyAge() {
            if( sPool.isEmpty() ) {
                return ++sEmptyAge;
            } else {
                sEmptyAge = 0;
                return 0;
            }
        }

    }


    private class View implements ObjectPool<T> {

        final Object mKey;

        View( Object key ) {
            mKey = key;
        }

        @Override
        public boolean offer( T item ) {
            return MultiCostPool.this.offer( mKey, item );
        }

        @Override
        public T poll() {
            return MultiCostPool.this.poll( mKey );
        }

    }

}
