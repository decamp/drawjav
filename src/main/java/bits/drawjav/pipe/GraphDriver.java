package bits.drawjav.pipe;

import bits.drawjav.ClockEventQueue;
import bits.microtime.PlayClock;
import bits.microtime.Ticker;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * @author Philip DeCamp
 */
public abstract class GraphDriver implements Channel, Ticker {


    public static GraphDriver createThreaded( PlayClock clock, AvGraph graph ) {
        return new ThreadedGraphDriver( clock, graph );
    }


    public static GraphDriver createStepping( PlayClock clock, AvGraph graph, Ticker optPreStep ) {
        return new SteppingGraphDriver( clock, graph, optPreStep );
    }


    final Object          mLock;
    final AvGraph         vGraph;
    final ClockEventQueue mEvents;
    final Ticker          mOptTicker;

    volatile boolean vOpen = false;


    GraphDriver( PlayClock optClock, AvGraph graph, Ticker optTicker ) {
        mLock      = graph;
        vGraph     = graph;
        mEvents    = new ClockEventQueue( mLock, optClock, 1024 );
        mOptTicker = optTicker;

        if( optClock != null ) {
            optClock.addListener( mEvents );
        }
    }



    public abstract void start();


    public abstract void tick();


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



    private static final class ThreadedGraphDriver extends GraphDriver {

        ThreadedGraphDriver( PlayClock optClock, AvGraph graph ) {
            super( optClock, graph, null );
        }

        @Override
        public void tick() {}


        public void start() {
            synchronized( mLock ) {
                if( vOpen ) {
                    return;
                }
                vOpen = true;
                new Thread( "GraphDriver" ) {
                    public void run() {
                        runLoop();
                    }
                }.start();
            }
        }


        private void runLoop() {
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
                    vGraph.waitForWork( 1000L );
                    break;
                }
            }
        }

    }


    private static final class SteppingGraphDriver extends GraphDriver {

        SteppingGraphDriver( PlayClock optClock, AvGraph graph, Ticker optTicker ) {
            super( optClock, graph, optTicker );
            vOpen = true;
        }


        @Override
        public void tick() {
            if( mOptTicker != null ) {
                mOptTicker.tick();
            }

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
                    return;
                }
            }
        }


        public void start() {}

    }

}

