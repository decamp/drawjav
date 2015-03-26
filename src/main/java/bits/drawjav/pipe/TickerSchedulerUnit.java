package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.microtime.*;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;

import java.io.IOException;
import java.util.*;


/**
 * This is the stepping version of SchedulerUnit. It must be updated on each tick before the
 * graph driver and will determine how many frames to pass through.
 *
 * @author Philip DeCamp
 */
public final class TickerSchedulerUnit implements SchedulerUnit {

    private final Object           mLock    = this;
    private final List<StreamNode> vStreams = new ArrayList<StreamNode>();

    private volatile boolean vOpen = false;
    private EventBus vBus;

    private int vNumNodesWithData = 0;


    public TickerSchedulerUnit() {}


    public int addStream( PlayClock clock, int queueCap ) {
        synchronized( mLock ) {
            StreamNode stream = new StreamNode( clock, queueCap );
            vStreams.add( stream );
            stream.vFireDataUpdate();
            return vStreams.size() - 1;
        }
    }

    @Override
    public void open( EventBus bus ) {
        synchronized( mLock ) {
            if( vOpen ) {
                return;
            }
            vOpen = true;
            vBus = bus;
        }
    }

    @Override
    public void close() {
        synchronized( mLock ) {
            if( !vOpen ) {
                return;
            }
            vOpen = false;
            for( StreamNode s: vStreams ) {
                s.vClear();
            }
            vStreams.clear();
        }
    }

    @Override
    public boolean isOpen() {
        return vOpen;
    }

    @Override
    public void clear() {
        synchronized( mLock ) {
            int len = vStreams.size();
            for( int i = 0; i < len; i++ ) {
                vStreams.get( i ).vClear();
            }
            vNumNodesWithData = 0;
        }
    }

    @Override
    public int inputNum() {
        synchronized( mLock ) {
            return vStreams.size();
        }
    }

    @Override
    public InPad input( int idx ) {
        synchronized( mLock ) {
            return vStreams.get( idx ).mIn;
        }
    }

    @Override
    public int outputNum() {
        synchronized( mLock ) {
            return vStreams.size();
        }
    }

    @Override
    public OutPad output( int idx ) {
        synchronized( mLock ) {
            return vStreams.get( idx ).mOut;
        }
    }

    @Override
    public void tick() {
        synchronized( mLock ) {
            final int len = vStreams.size();
            for( int i = 0; i < len; i++ ) {
                vStreams.get( i ).vTick();
            }
        }
    }



    private final class StreamNode {
        final PlayClock        mClock;
        final InHandler        mIn            = new InHandler();
        final OutHandler       mOut           = new OutHandler();
        final InPadReadyEvent  mInReadyEvent  = new InPadReadyEvent( mIn );
        final OutPadReadyEvent mOutReadyEvent = new OutPadReadyEvent( mOut );

        final int mQueueCap;
        final Deque<Packet> vQueue = new ArrayDeque<Packet>();
        long vQueueMicros;

        long    vClockMicros;
        boolean vClockForward;

        boolean vHasData  = false;

        boolean vInReady  = true;
        boolean vOutReady = false;


        StreamNode( PlayClock clock, int queueCap ) {
            mClock        = clock;
            mQueueCap     = queueCap;
            vClockMicros  = clock.micros();
            vClockForward = clock.rate().mNum > 0;
        }


        void vTick() {
            vClockMicros = mClock.micros();
            boolean forward = mClock.rate().mNum >= 0;
            if( forward != vClockForward ) {
                vClockForward = forward;
                vClear();
            }

            if( vHasData ) {
                vFireUnitUpdate();
            }
        }


        void vClear() {
            if( vQueue.isEmpty() ) {
                return;
            }
            while( !vQueue.isEmpty() ) {
                vQueue.remove().deref();
            }
            vFireDataUpdate();
        }


        void vFireUnitUpdate() {
            boolean ready;
            int size = vQueue.size();

            ready = size < mQueueCap && vNumNodesWithData < vStreams.size();
            if( ready != vInReady ) {
                vInReady = ready;
                if( ready ) {
                    vBus.post( mInReadyEvent );
                }
            }

            ready = size > 0 && ( vClockForward ? vQueueMicros <= vClockMicros : vQueueMicros > vClockMicros );
            if( ready != vOutReady ) {
                vOutReady = ready;
                if( ready ) {
                    vBus.post( mOutReadyEvent );
                }
            }
        }


        private void vFireDataUpdate() {
            boolean hasData = !vQueue.isEmpty();
            if( hasData ) {
                vQueueMicros = vClockForward ? vQueue.peek().startMicros() : vQueue.peek().stopMicros();
            }

            if( hasData != vHasData ) {
                vHasData = hasData;

                if( hasData ) {
                    // If all nodes in this unit have data, none of the nodes can accept more data
                    // and all must be updated.
                    if( ++vNumNodesWithData == vStreams.size() ) {
                        for( StreamNode node : vStreams ) {
                            node.vFireUnitUpdate();
                        }
                        return;
                    }
                } else {
                    // If all nodes in this unit had data, they were unable to accept more data.
                    // Now that one of the nodes can accept input, they must all be updated.
                    if( vNumNodesWithData-- == vStreams.size() ) {
                        for( StreamNode node: vStreams ) {
                            node.vFireUnitUpdate();
                        }
                        return;
                    }
                }
            }

            vFireUnitUpdate();
        }


        private final class InHandler implements InPad<Packet> {
            @Override
            public int status() {
                synchronized( mLock ) {
                    return vInReady ? OKAY : WAIT;
                }
            }

            @Override
            public int offer( Packet packet ) {
                synchronized( mLock ) {
                    if( packet == null ) {
                        return OKAY;
                    }

                    if( !vInReady ) {
                        return WAIT;
                    }

                    packet.ref();
                    vQueue.offer( packet );
                    int size = vQueue.size();
                    if( size == 1 || size == mQueueCap ) {
                        vFireDataUpdate();
                    }

                    return OKAY;
                }
            }

            @Override
            public void config( StreamFormat stream ) throws IOException {}

            @Override
            public boolean isThreaded() {
                return true;
            }

            @Override
            public Object lock() {
                return mLock;
            }

            @Override
            public Exception exception() {
                return null;
            }
        }


        private final class OutHandler implements OutPad {
            @Override
            public int status() {
                synchronized( mLock ) {
                    return vOutReady ? OKAY : WAIT;
                }
            }

            @Override
            public int poll( Refable[] out ) {
                synchronized( mLock ) {
                    if( !vOutReady ) {
                        return WAIT;
                    }

                    int size = vQueue.size();
                    out[0] = vQueue.remove();
                    if( size == 1 || size == mQueueCap ) {
                        vFireDataUpdate();
                    }

                    return OKAY;
                }
            }

            @Override
            public void config( StreamFormat stream ) throws IOException {}

            @Override
            public boolean isThreaded() {
                return true;
            }

            @Override
            public Object lock() {
                return mLock;
            }

            @Override
            public Exception exception() {
                return null;
            }
        }
    }

}
