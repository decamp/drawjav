package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.microtime.*;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class SchedulerUnit implements AvUnit {


    private static final Logger sLog = Logger.getLogger( SchedulerUnit.class.getName() );


    private final Object                    mLock      = this;
    private final Map<PlayClock, ClockNode> vClocks    = new HashMap<PlayClock, ClockNode>();
    private final PrioHeap<ClockNode>       vClockHeap = new PrioHeap<ClockNode>();
    private final List<StreamHandler>       vStreams   = new ArrayList<StreamHandler>();

    private Command vCommandPool     = null;
    private int     vCommandPoolSize = 0;
    private int     vCommandPoolCap  = 1024;

    private volatile boolean vOpen = false;
    private EventBus mBus;

    public SchedulerUnit() {}


    public int addStream( PlayClock clock, int queueCap ) {
        synchronized( mLock ) {
            ClockNode node = vClocks.get( clock );
            if( node == null ) {
                node = new ClockNode( clock );
                vClocks.put( clock, node );
            }
            vClockHeap.offer( node );
            StreamHandler stream = new StreamHandler( node, queueCap );
            vStreams.add( stream );
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
            mBus = bus;
            new Thread( "SchedulerUnit" ) {
                public void run() {
                    runLoop();
                }
            }.start();
        }
    }

    @Override
    public void close() {
        synchronized( mLock ) {
            if( !vOpen ) {
                return;
            }
            vOpen = false;
            mLock.notifyAll();

            for( ClockNode c: vClocks.values() ) {
                c.vClear();
            }
            vClocks.clear();
            vClockHeap.clear();

            for( StreamHandler s: vStreams ) {
                s.vClear();
            }
            vStreams.clear();
            vCommandPool = null;
            vCommandPoolSize = 0;
        }
    }

    @Override
    public boolean isOpen() {
        return vOpen;
    }

    @Override
    public void clear() {
        synchronized( mLock ) {
            int len = vClockHeap.size();
            for( int i = 0; i < len; i++ ) {
                //ClockNode node = (ClockNode)vClockHeap.mArr[i];
                ClockNode node = vClockHeap.get( i );
                node.clear();
            }
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


    private void runLoop() {
        synchronized( mLock ) {
            while( vOpen ) {
                try {

                    ClockNode n = vClockHeap.peek();
                    if( n == null || n.vNextExec == Long.MAX_VALUE ) {
                        mLock.wait();
                        continue;
                    }

                    long waitFor = n.vComputeWaitMillis();
                    if( waitFor > 10L ) {
                        mLock.wait( waitFor );
                        continue;
                    }

                    n.vExecNext();

                } catch( InterruptedException ignore ) {}
            }
        }
    }


    private Command vCommandPoolGet() {
        if( vCommandPoolSize-- > 0 ) {
            Command ret = vCommandPool;
            vCommandPool = ret.mNext;
            ret.mNext = null;
            ret.mPooled = false;
            return ret;
        }

        vCommandPoolSize = 0;
        return new Command();
    }


    private void vCommandPoolOffer( Command c ) {
        c.mPooled = true;
        if( c.mStream != null ) {
            c.mStream.decrementCount();
        }

        if( c.mPacket != null ) {
            c.mPacket.deref();
            c.mPacket = null;
        }

        if( vCommandPoolSize++ < vCommandPoolCap ) {
            c.mNext = vCommandPool;
            vCommandPool = c;
        } else {
            vCommandPoolSize--;
        }
    }



    private final class ClockNode extends HeapNode implements SyncClockControl, Comparable<ClockNode> {
        final PlayClock mClock;
        final PrioHeap<Command> mHeap = new PrioHeap<Command>();
        int     vStreamNum = 0;
        long    vNextExec  = Long.MAX_VALUE;
        boolean vForward   = true;


        public ClockNode( PlayClock clock ) {
            mClock = clock;
        }


        @Override
        public void clockStart( long execMicros ) {
            synchronized( mLock ) {
                vUpdate();
            }
        }

        @Override
        public void clockStop( long execMicros ) {
            synchronized( mLock ) {
                vUpdate();
            }
        }

        @Override
        public void clockSeek( long execMicros, long seekMicros ) {
            synchronized( mLock ) {
                if( vClear() ) {
                    vUpdate();
                }
            }
        }

        @Override
        public void clockRate( long execMicros, Frac rate ) {
            synchronized( mLock ) {
                boolean forward = rate.mNum >= 0;
                if( forward != vForward ) {
                    vForward = forward;
                    vClear();
                }
                vUpdate();
            }
        }


        public void clear() {
            synchronized( mLock ) {
                if( vClear() ) {
                    vUpdate();
                }
            }
        }


        long vComputeWaitMillis() {
            return ( vNextExec - mClock.masterMicros() ) / 1000L;
        }


        void vOffer( Command c ) {
            mHeap.offer( c );
            //if( mHeap.mArr[0] == c ) {
            if( mHeap.peek() == c ) {
                vUpdate();
            }
        }


        private boolean vClear() {
            //int len = mHeap.mSize;
            int len = mHeap.size();
            if( len == 0 ) {
                return false;
            }

            for( int i = 0; i < len; i++ ) {
                //vCommandPoolOffer( (Command)mHeap.mArr[i] );
                vCommandPoolOffer( mHeap.remove() );
            }

            mHeap.clear();
            return true;
        }


        private void vUpdate() {
            Command c = mHeap.peek();
            long pts = c == null ? Long.MAX_VALUE : mClock.toMaster( c.mDts );

            if( pts == vNextExec ) {
                return;
            }

            vNextExec = pts;
            vClockHeap.reschedule( this );
            mLock.notify();
        }


        private void vExecNext() {
            Command c = mHeap.remove();
            vUpdate();
            c.mStream.vQueueOutput( c );
        }


        public int compareTo( ClockNode node ) {
            return vNextExec < node.vNextExec ? -1 : 1;
        }

    }


    private final class StreamHandler {
        final ClockNode mNode;

        final InHandler        mIn       = new InHandler();
        final OutHandler       mOut      = new OutHandler();
        final InPadReadyEvent  mInReady  = new InPadReadyEvent( mIn );
        final OutPadReadyEvent mOutReady = new OutPadReadyEvent( mOut );

        final int mStreamCap;
        int mStreamSize;

        Command vReadyHead = null;
        Command vReadyTail = null;


        StreamHandler( ClockNode node, int queueCap ) {
            mNode = node;
            mStreamCap = queueCap;
        }


        void vQueueOutput( Command c ) {
            c.mNext = null;

            if( vReadyHead == null ) {
                vReadyHead = vReadyTail = c;
                mBus.post( mOutReady );
            } else {
                vReadyTail.mNext = c;
                vReadyTail = c;
            }
        }


        void vClear() {
            Command head = vReadyHead;
            vReadyHead = null;
            vReadyTail = null;

            while( head != null ) {
                Command next = head.mNext;
                vCommandPoolOffer( next );
                head = next;
            }

            mStreamSize = 0;
        }


        void decrementCount() {
            if( mStreamSize-- == mStreamCap ) {
                mBus.post( mInReady );
            }
        }


        private final class InHandler implements InPad<Packet> {
            @Override
            public int status() {
                synchronized( mLock ) {
                    return mStreamSize < mStreamCap ? OKAY : WAIT;
                }
            }

            @Override
            public int offer( Packet packet ) {
                synchronized( mLock ) {
                    if( packet == null ) {
                        return OKAY;
                    }

                    if( mStreamSize++ >= mStreamCap ) {
                        mStreamSize--;
                        return WAIT;
                    }

                    Command c = vCommandPoolGet();
                    c.vInit( StreamHandler.this, packet, mNode.vForward );
                    mNode.vOffer( c );
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
                    return vReadyHead == null ? WAIT : OKAY;
                }
            }

            @Override
            public int poll( Refable[] out ) {
                synchronized( mLock ) {
                    Command head = vReadyHead;
                    if( head == null ) {
                        return WAIT;
                    }

                    out[0] = head.mPacket;
                    // Set packet to null before disposing command to avoid unnecessary packet ref()/deref()
                    head.mPacket = null;

                    vReadyHead = head.mNext;
                    if( vReadyHead == null ) {
                        vReadyTail = null;
                    }

                    vCommandPoolOffer( head );
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


    private class Command extends HeapNode implements Comparable<Command> {
        StreamHandler mStream = null;
        Packet mPacket = null;

        boolean mForward;
        long    mDts;
        Command mNext = null;
        boolean mPooled = false;


        void vInit( StreamHandler stream, Packet p, boolean forward ) {
            mStream = stream;
            mPacket = p;
            p.ref();
            mForward = forward;
            mDts = forward ? p.startMicros() : p.stopMicros();
        }

        @Override
        public int compareTo( Command command ) {
            if( mForward ) {
                return mDts < command.mDts ? -1 : 1;
            } else {
                return mDts >= command.mDts ? -1 : 1;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            if( !mPooled ) {
                synchronized( DrawPacket.class ) {
                    sLog.warning( "Command finalized without being destroyed." );
                }
            }

            super.finalize();
        }
    }

}
