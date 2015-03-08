package bits.drawjav;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * @author Philip DeCamp
 */
public class TestCostPool {

    static final int COST_CAP    = 4;
    static final int WARNING_CAP = 6;


    @Test
    public void testOfferPoll() {
        CostPool<CostItem> pool = createPool();
        assertTrue( pool.poll() == null );
        CostItem item;

        // Test offer to pool A
        for( int i = 0; i < COST_CAP; i++ ) {
            item = new CostItem( 1, pool );
            pool.allocated( item );
            item.deref();

            assertEquals( i + 1, pool.allocatedCost() );
            assertEquals( i + 1, pool.poolCost() );
        }

        // Overflow pool.
        for( int i = 0; i < 3; i++ ) {
            item = new CostItem( 1, pool );
            pool.allocated( item );
            item.deref();

            assertEquals( COST_CAP, pool.allocatedCost() );
            assertEquals( COST_CAP, pool.poolCost() );
        }


        // Test poll from pool A.
        for( int i = 0; i < 5; i++ ) {
            item = pool.poll();
            assertTrue( item != null );
            assertEquals( COST_CAP, pool.allocatedCost() );
            assertEquals( COST_CAP - 1, pool.poolCost() );
            item.deref();
        }

        // Drain A.
        for( int i = 0; i < COST_CAP; i++ ) {
            item = pool.poll();
            assertTrue( item != null );
            assertEquals( COST_CAP, pool.allocatedCost() );
            assertEquals( COST_CAP - 1, pool.poolCost() );
            // NO DEREF
        }

        assertEquals( null, pool.poll() );
        assertFalse( pool.hasWarned() );
    }

    @Test
    public void testWarning() {
        CostPool<CostItem> pool = createPool();
        CostItem item = null;

        for( int i = 0; i < WARNING_CAP; i++ ) {
            assertFalse( pool.hasWarned() );
            item = new CostItem( 1, null );
            pool.allocated( item );
        }

        assertTrue( pool.hasWarned() );
    }


    static CostPool<CostItem> createPool() {
        return new CostPool<CostItem>( COST_CAP, WARNING_CAP, CostItem.METRIC );
    }

}

