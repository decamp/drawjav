package bits.drawjav;

import bits.util.ref.ObjectPool;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * @author Philip DeCamp
 */
public class MultiCostPoolTest {

    static final int COST_CAP    = 4;
    static final int WARNING_CAP = 6;


    @Test
    public void testOfferPoll() {
        MultiCostPool<CostItem> pools = createPool();
        ObjectPool<CostItem> poolA = pools.pool( "a" );
        assertTrue( poolA.poll() == null );

        // Test offer to pool A
        for( int i = 0; i < COST_CAP; i++ ) {
            CostItem item = new CostItem( 1, poolA );
            pools.allocated( item );
            item.deref();

            assertEquals( i + 1, pools.allocatedCost() );
            assertEquals( i + 1, pools.poolCost() );
            assertEquals( i + 1, pools.poolCost( "a" ) );
        }

        // Test overflow.
        for( int i = 0; i < 3; i++ ) {
            CostItem item = new CostItem( 1, poolA );
            pools.allocated( item );
            item.deref();

            assertEquals( COST_CAP, pools.allocatedCost() );
            assertEquals( COST_CAP, pools.poolCost() );
            assertEquals( COST_CAP, pools.poolCost( "a" ) );
        }

        // Test poll from pool A.
        for( int i = 0; i < 5; i++ ) {
            CostItem item = poolA.poll();
            assertTrue( item != null );
            assertEquals( COST_CAP, pools.allocatedCost() );
            assertEquals( COST_CAP - 1, pools.poolCost() );
            assertEquals( COST_CAP - 1, pools.poolCost( "a" ) );
            item.deref();
        }

        // Drain A.
        for( int i = 0; i < COST_CAP; i++ ) {
            CostItem item = poolA.poll();
            assertTrue( item != null );
            assertEquals( COST_CAP, pools.allocatedCost() );
            assertEquals( COST_CAP - 1 - i, pools.poolCost() );
            assertEquals( COST_CAP - 1 - i, pools.poolCost( "a" ) );
            // NO DEREF
        }

        assertEquals( null, poolA.poll() );
    }

    @Test
    public void testDisposeLeastRecent() {
        CostItem item;
        MultiCostPool<CostItem> pools = createPool();
        ObjectPool<CostItem> poolA = pools.pool( "a" );
        ObjectPool<CostItem> poolB = pools.pool( "b" );
        ObjectPool<CostItem> poolC = pools.pool( "c" );
        assertTrue( poolA.poll() == null );
        int costA = COST_CAP / 2;
        int costB = COST_CAP - costA;

        // Instantiate pool B.
        {
            item = new CostItem( 1, poolB );
            pools.allocated( item );
            item.deref();
            item = pools.poll( "b" );
            pools.dispose( item );

            assertEquals( 0, pools.allocatedCost() );
            assertEquals( 0, pools.poolCost() );
            assertEquals( 0, pools.poolCost( "a" ) );
            assertEquals( 0, pools.poolCost( "b" ) );
            assertEquals( 0, pools.poolCost( "c" ) );
        }

        // Test pool A.
        for( int i = 0; i < COST_CAP; i++ ) {
            item = new CostItem( 1, poolA );
            pools.allocated( item );
            item.deref();

            assertEquals( i + 1, pools.allocatedCost() );
            assertEquals( i + 1, pools.poolCost() );
            assertEquals( i + 1, pools.poolCost( "a" ) );
            assertEquals( 0, pools.poolCost( "b" ) );
            assertEquals( 0, pools.poolCost( "c" ) );
        }

        // Add one more.
        item = new CostItem( 1, poolA );
        pools.allocated( item );
        item.deref();

        new CostItem( 1, poolA ).deref();
        assertEquals( COST_CAP, pools.allocatedCost() );
        assertEquals( COST_CAP, pools.poolCost() );
        assertEquals( COST_CAP, pools.poolCost( "a" ) );
        assertEquals( 0, pools.poolCost( "b" ) );
        assertEquals( 0, pools.poolCost( "c" ) );

        // Activate pool A.
        pools.poll( "a" ).deref();
        assertEquals( COST_CAP, pools.allocatedCost() );
        assertEquals( COST_CAP, pools.poolCost() );
        assertEquals( COST_CAP, pools.poolCost( "a" ) );
        assertEquals( 0, pools.poolCost( "b" ) );
        assertEquals( 0, pools.poolCost( "c" ) );

        // Add some to pool b.
        for( int i = 0; i < 3; i++ ) {
            item = new CostItem( 1, poolB );
            pools.allocated( item );
            item.deref();

            assertEquals( COST_CAP, pools.allocatedCost() );
            assertEquals( COST_CAP, pools.poolCost() );
            assertEquals( COST_CAP - 1, pools.poolCost( "a" ) );
            assertEquals( 1, pools.poolCost( "b" ) );
            assertEquals( 0, pools.poolCost( "c" ) );
        }

        // Activate pool b.
        pools.poll( "b" ).deref();

        // Add some more to pool b.
        for( int i = 1; i < costB; i++ ) {
            item = new CostItem( 1, poolB );
            pools.allocated( item );
            item.deref();

            assertEquals( COST_CAP, pools.allocatedCost() );
            assertEquals( COST_CAP, pools.poolCost() );
            assertEquals( COST_CAP - i - 1, pools.poolCost( "a" ) );
            assertEquals( i + 1, pools.poolCost( "b" ) );
            assertEquals( 0, pools.poolCost( "c" ) );
        }

        // pool "b" is most activated. Allocate into pool "c" and make sure "a" is shrunk.
        item = new CostItem( 1, poolC );
        pools.allocated( item );
        item.deref();

        assertEquals( COST_CAP, pools.allocatedCost() );
        assertEquals( COST_CAP, pools.poolCost() );
        assertEquals( costA - 1, pools.poolCost( "a" ) );
        assertEquals( costB, pools.poolCost( "b" ) );
        assertEquals( 1, pools.poolCost( "c" ) );

        // Now activate pool "a" and make sure "b" is shrunk.
        pools.poll( "a" ).deref();

        item = new CostItem( 1, poolC );
        pools.allocated( item );
        item.deref();

        assertEquals( COST_CAP, pools.allocatedCost() );
        assertEquals( COST_CAP, pools.poolCost() );
        assertEquals( costA - 1, pools.poolCost( "a" ) );
        assertEquals( costB - 1, pools.poolCost( "b" ) );
        assertEquals( 2, pools.poolCost( "c" ) );

    }

    @Test
    public void testWarning() {
        MultiCostPool<CostItem> pools = createPool();
        CostItem item = null;

        for( int i = 0; i < WARNING_CAP; i++ ) {
            assertFalse( pools.hasWarned() );
            item = new CostItem( 1, null );
            pools.allocated( item );
        }

        assertTrue( pools.hasWarned() );
    }


    static MultiCostPool<CostItem> createPool() {
        return new MultiCostPool<CostItem>( COST_CAP, WARNING_CAP, CostItem.METRIC );
    }

}

