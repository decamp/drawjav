package cogmac.javdraw;

public interface PipeHandle {
    public StreamHandle srcHandle();
    public StreamHandle dstHandle();
    public Sink<Packet> sink();
}
