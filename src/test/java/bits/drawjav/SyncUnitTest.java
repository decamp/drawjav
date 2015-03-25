package bits.drawjav;

import bits.drawjav.pipe.TickerSchedulerUnit;
import bits.jav.Jav;
import bits.microtime.*;
import com.google.common.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;

import static bits.drawjav.pipe.Pad.*;
import static org.junit.Assert.*;


/**
 * @author Philip DeCamp
 */
@SuppressWarnings( "unchecked" )
public class SyncUnitTest {

    final StreamFormat mFormat = new StreamFormat( Jav.AVMEDIA_TYPE_AUDIO );
    final PacketAllocator<DrawPacket> mAlloc = OneFormatAllocator.createPacketLimited( 1000 );

    @Before
    public void init() {
        Jav.init();
    }

    @Test
    public void testTick() {
        ManualClock clock = new ManualClock( 0 );
        FullClock full = new FullClock( clock );
        full.clockStart( 0 );

        TickerSchedulerUnit unit = new TickerSchedulerUnit();
        EventBus bus = new EventBus();

        for( int i = 0; i < 2; i++ ) {
            unit.addStream( full, 2 );
        }

        unit.open( bus );

        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );
        assertEquals( OKAY, unit.input( 0 ).offer( alloc( 0, 100 ) ) );
        assertPadStatus( unit, OKAY, OKAY, OKAY, WAIT );
        clock.micros( -100L );
        unit.tick();
        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );
        clock.micros( 100L );
        unit.tick();
        assertPadStatus( unit, OKAY, OKAY, OKAY, WAIT );
    }

    @Test
    public void testFill() {
        ManualClock clock = new ManualClock( 0 );
        FullClock full = new FullClock( clock );
        full.clockStart( 0 );

        TickerSchedulerUnit unit = new TickerSchedulerUnit();
        EventBus bus = new EventBus();

        for( int i = 0; i < 2; i++ ) {
            unit.addStream( full, 2 );
        }

        unit.open( bus );
        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );

        // Fill input 0
        assertEquals( OKAY, unit.input( 0 ).offer( alloc( 0, 100 ) ) );
        assertPadStatus( unit, OKAY, OKAY, OKAY, WAIT );
        assertEquals( OKAY, unit.input( 0 ).offer( alloc( 100, 200 ) ) );
        assertPadStatus( unit, WAIT, OKAY, OKAY, WAIT );
        assertEquals( WAIT, unit.input( 0 ).offer( alloc( 200, 300 ) ) );
        assertPadStatus( unit, WAIT, OKAY, OKAY, WAIT );

        // Fill input 1
        assertEquals( OKAY, unit.input( 1 ).offer( alloc( 0, 100 ) ) );
        // Note: as soon as both inputs have at least one packet, they should both turn request to wait.
        assertPadStatus( unit, WAIT, OKAY, WAIT, OKAY );
        assertEquals( WAIT, unit.input( 1 ).offer( alloc( 100, 200 ) ) );

        // Drain 0.
        Packet[] out = { null };

        assertEquals( OKAY, unit.output( 0 ).poll( out ) );
        assertPadStatus( unit, WAIT, WAIT, WAIT, OKAY );
        out[0].deref();
        out[0] = null;

        // Out of data for now. Move clock to get next packet from 0.
        clock.micros( 1000L );
        unit.tick();
        assertPadStatus( unit, WAIT, OKAY, WAIT, OKAY );
        assertEquals( OKAY, unit.output( 0 ).poll( out ) );
        assertPadStatus( unit, OKAY, WAIT, OKAY, OKAY );
        out[0].deref();
        out[0] = null;

        assertEquals( OKAY, unit.output( 1 ).poll( out ) );
        out[0].deref();
        out[0] = null;
        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );
    }

    @Test
    public void testClear() {
        ManualClock clock = new ManualClock( 0 );
        FullClock full = new FullClock( clock );
        full.clockStart( 0 );

        TickerSchedulerUnit unit = new TickerSchedulerUnit();
        EventBus bus = new EventBus();

        for( int i = 0; i < 2; i++ ) {
            unit.addStream( full, 2 );
        }

        unit.open( bus );
        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );

        // Fill input 0
        assertEquals( OKAY, unit.input( 0 ).offer( alloc( 0, 100 ) ) );
        assertEquals( OKAY, unit.input( 0 ).offer( alloc( 100, 200 ) ) );
        assertEquals( OKAY, unit.input( 1 ).offer( alloc( 0, 100 ) ) );

        unit.clear();
        assertPadStatus( unit, OKAY, WAIT, OKAY, WAIT );
    }


    static void assertPadStatus( TickerSchedulerUnit unit, int... states ) {
        int len = states.length / 2;
        assertEquals( len, unit.inputNum() );

        for( int i = 0; i < len; i++ ) {
            assertEquals( states[i*2  ], unit.input( i ).status() );
            assertEquals( states[i*2+1], unit.output( i ).status() );
        }
    }


    Packet alloc( long start, long stop ) {
        DrawPacket ret = mAlloc.alloc( mFormat, 0 );
        ret.init( mFormat, start, stop, true );
        return ret;
    }

}
