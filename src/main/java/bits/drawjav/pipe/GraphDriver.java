package bits.drawjav.pipe;

import bits.drawjav.ClockEventQueue;
import bits.microtime.PlayClock;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * @author Philip DeCamp
 */
public class GraphDriver implements Channel {

    private final Object mLock;

    private final AvGraph         vGraph;
    private final ClockEventQueue mEvents;

    private volatile boolean vOpen = false;


    public GraphDriver( Object lock, AvGraph graph, PlayClock optClock ) {
        mLock = lock == null ? this : lock;
        vGraph = graph;
        mEvents = new ClockEventQueue( mLock, optClock, 1024 );
        if( optClock != null ) {
            optClock.addListener( mEvents );
        }
    }


    public void postEvent( Object event ) {
        mEvents.offer( event );
    }


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
