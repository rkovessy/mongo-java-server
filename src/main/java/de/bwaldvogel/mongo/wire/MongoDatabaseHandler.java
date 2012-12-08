package de.bwaldvogel.mongo.wire;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;

import de.bwaldvogel.mongo.backend.MongoBackend;
import de.bwaldvogel.mongo.exception.MongoServerException;
import de.bwaldvogel.mongo.wire.message.MessageHeader;
import de.bwaldvogel.mongo.wire.message.MongoDelete;
import de.bwaldvogel.mongo.wire.message.MongoInsert;
import de.bwaldvogel.mongo.wire.message.MongoQuery;
import de.bwaldvogel.mongo.wire.message.MongoReply;
import de.bwaldvogel.mongo.wire.message.MongoServer;
import de.bwaldvogel.mongo.wire.message.MongoUpdate;

public class MongoDatabaseHandler extends SimpleChannelUpstreamHandler {

    private final AtomicInteger idSequence = new AtomicInteger();
    private final MongoBackend mongoBackend;

    private static final Logger log = Logger.getLogger( MongoWireProtocolHandler.class );
    private ChannelGroup channelGroup;
    private long started;
    private Date startDate;

    public MongoDatabaseHandler(MongoBackend mongoBackend , ChannelGroup channelGroup) {
        this.mongoBackend = mongoBackend;
        this.channelGroup = channelGroup;
        this.started = System.nanoTime();
        this.startDate = new Date();
    }

    @Override
    public void channelOpen( ChannelHandlerContext ctx , ChannelStateEvent e ) throws Exception {
        Channel channel = e.getChannel();
        channelGroup.add( channel );
        log.info( "client " + channel + " connected" );
        super.channelClosed( ctx, e );
    }

    @Override
    public void channelClosed( ChannelHandlerContext ctx , ChannelStateEvent e ) throws Exception {
        Channel channel = e.getChannel();
        log.info( "channel " + channel + " closed" );
        mongoBackend.handleClose( channel );
        super.channelClosed( ctx, e );
    }

    @Override
    public void messageReceived( ChannelHandlerContext ctx , MessageEvent event ) throws MongoServerException {
        final Object object = event.getMessage();
        if ( object instanceof MongoQuery ) {
            event.getChannel().write( handleQuery( event.getChannel(), (MongoQuery) object ) );
        }
        else if ( object instanceof MongoInsert ) {
            MongoInsert insert = (MongoInsert) object;
            mongoBackend.handleInsert( insert );
        }
        else if ( object instanceof MongoDelete ) {
            MongoDelete delete = (MongoDelete) object;
            mongoBackend.handleDelete( delete );
        }
        else if ( object instanceof MongoUpdate ) {
            MongoUpdate update = (MongoUpdate) object;
            mongoBackend.handleUpdate( update );
        }
        else {
            throw new MongoServerException( "unknown message: " + object );
        }
    }

    public Date getStartDate() {
        return startDate;
    }

    protected MongoReply handleQuery( Channel channel , MongoQuery query ) {
        List<BSONObject> documents = new ArrayList<BSONObject>();
        MessageHeader header = new MessageHeader( idSequence.incrementAndGet() , query.getHeader().getRequestID() );
        try {
            if ( query.getCollectionName().startsWith( "$cmd" ) ) {
                documents.add( handleCommand( channel, query, documents ) );
            }
            else {
                for ( BSONObject obj : mongoBackend.handleQuery( query ) ) {
                    documents.add( obj );
                }
            }
        }
        catch ( MongoServerException e ) {
            log.error( "failed to handle query " + query, e );
            documents.add( e.createBSONObject( channel, query.getQuery() ) );
        }

        return new MongoReply( header , documents );
    }

    protected BSONObject handleCommand( Channel channel , MongoQuery query , List<BSONObject> documents ) throws MongoServerException {
        String collectionName = query.getCollectionName();
        if ( collectionName.equals( "$cmd.sys.inprog" ) ) {
            Collection<BSONObject> currentOperations = mongoBackend.getCurrentOperations( query );
            return new BasicBSONObject( "inprog" , currentOperations );
        }

        if ( collectionName.equals( "$cmd" ) ) {
            String command = query.getQuery().keySet().iterator().next();
            if ( command.equals( "serverStatus" ) ) {
                return getServerStatus();
            }
            else {
                return mongoBackend.handleCommand( channel, query.getDatabaseName(), command, query.getQuery() );
            }
        }

        throw new MongoServerException( "unknown collection: " + collectionName );
    }

    private BSONObject getServerStatus() throws MongoServerException {
        BSONObject serverStatus = new BasicBSONObject();
        try {
            serverStatus.put( "host", InetAddress.getLocalHost().getHostName() );
        }
        catch ( UnknownHostException e ) {
            throw new MongoServerException( "failed to get hostname" , e );
        }
        serverStatus.put( "version", MongoServer.VERSION );
        serverStatus.put( "process", "java" );
        serverStatus.put( "pid", getProcessId() );

        serverStatus.put( "uptime", Integer.valueOf( (int) TimeUnit.NANOSECONDS.toSeconds( System.nanoTime() - started ) ) );
        serverStatus.put( "uptimeMillis", Long.valueOf( TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - started ) ) );
        serverStatus.put( "localTime", new Date() );

        BSONObject connections = new BasicBSONObject();
        connections.put( "current", Integer.valueOf( channelGroup.size() ) );

        serverStatus.put( "connections", connections );

        BSONObject cursors = new BasicBSONObject();
        cursors.put( "totalOpen", Integer.valueOf( 0 ) ); // TODO

        serverStatus.put( "cursors", cursors );

        serverStatus.put( "ok", Integer.valueOf( 1 ) );

        return serverStatus;
    }

    private Integer getProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if ( runtimeName.contains( "@" ) ) {
            return Integer.valueOf( runtimeName.substring( 0, runtimeName.indexOf( '@' ) ) );
        }
        return Integer.valueOf( 0 );
    }
}