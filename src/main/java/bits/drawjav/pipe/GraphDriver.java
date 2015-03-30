package bits.drawjav.pipe;

import bits.drawjav.ClockEventQueue;
import bits.microtime.*;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * Drives AvGraph processing operations. GraphDriver operates in one of two modes:
 * <p>
 * <b>Manual Mode</b>: User must repeatedly call {@code tick()}. On each call, GraphDriver will process data until
 * nothing is left to be done, then return. Normally, {@code tick()} will be called on each rendering frame
 * or each clock update.
 * <p>
 * <b>Threaded Mode</b>: Entered by calling {@code startThreadedMode()}. GraphDriver will use an internal thread to
 * continuously process data until {@code close()} is called. In this mode, calling {@code tick()} will
 * call listener Tickers, but will have no other effect.
 *
 * @author Philip DeCamp
 */
public final class GraphDriver implements Channel, Ticker {

    final Object          mLock;
    final AvGraph         vGraph;
    final ClockEventQueue mEvents;

    volatile Ticker  vTicker   = null;
    volatile boolean vThreaded = false;
    volatile boolean vOpen     = true;


    public GraphDriver( PlayClock optClock, AvGraph graph ) {
        if( graph == null ) {
            throw new NullPointerException();
        }

        mLock   = graph;
        vGraph  = graph;
        mEvents = new ClockEventQueue( graph, optClock, 1024 );
    }



    public void startThreadedMode() {
        synchronized( mLock ) {
            if( vThreaded || !vOpen ) {
                return;
            }

            vThreaded = true;
            new Thread( "GraphDriver" ) {
                public void run() {
                    step( true );
                }
            }.start();
        }
    }


    public void tick() {
        Ticker t = vTicker;
        if( t !=  null ) {
            t.tick();
        }

        if( vThreaded ) {
            return;
        }

        step( false );
    }

    /**
     * "Child Tickers" can be added to the GraphDriver. These tickers will be updated on each call
     * to {@code GraphDriver.tick()} prior to processing graph data.
     *
     * @param t Ticker to call each {@code tick()} step.
     */
    public void addTicker( Ticker t ) {
        synchronized( mLock ) {
            vTicker = TickCaster.add( vTicker, t );
        }
    }


    public void removeTicker( Ticker t ) {
        synchronized( mLock ) {
            vTicker = TickCaster.remove( vTicker, t );
        }
    }


    public void postEvent( Object event ) {
        mEvents.offer( event );
    }

    @Override
    public boolean isOpen() {
        return vOpen;
    }

    @Override
    public void close() throws IOException {
        synchronized( mLock ) {
            if( !vOpen ) {
                return;
            }
            vOpen = false;
            mLock.notifyAll();
        }
    }


    private void step( boolean wait ) {
        while( true ) {
            synchronized( mLock ) {
                if( !vOpen ) {
                    return;
                }

                // Process events.
                while( true ) {
                    Object e = mEvents.poll();
                    if( e == null ) {
                        break;
                    }
                    vGraph.postEvent( e );
                }
            }

            switch( vGraph.step() ) {
            case AvGraph.WAIT:
            case AvGraph.FINISHED:
                if( !wait ) {
                    return;
                }
                vGraph.waitForWork( 1000L );
                break;
            }
        }
    }

}

