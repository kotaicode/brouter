package btools.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.StringTokenizer;

import btools.router.OsmNodeNamed;
import btools.router.OsmTrack;
import btools.router.RoutingContext;
import btools.router.RoutingEngine;
import btools.server.request.RequestHandler;
import btools.server.request.ServerHandler;

public class RouteServer extends Thread
{
	public ServiceContext serviceContext;

  private Socket clientSocket = null;
  private RoutingEngine cr = null;

  public void stopRouter()
  {
    RoutingEngine e = cr;
    if ( e != null ) e.terminate();
  }
    
  public void run()
  {
          BufferedReader br = null;
          BufferedWriter bw = null;
          try
          {
            br = new BufferedReader( new InputStreamReader( clientSocket.getInputStream() ) );
            bw = new BufferedWriter( new OutputStreamWriter( clientSocket.getOutputStream() ) );

            // we just read the first line
            String getline = br.readLine();
            if ( getline == null || getline.startsWith("GET /favicon.ico") )
            {
            	return;
            }

            InetAddress ip = clientSocket.getInetAddress();
            System.out.println( "ip=" + (ip==null ? "null" : ip.toString() ) + " -> " + getline );

            String url = getline.split(" ")[1];
            HashMap<String,String> params = getUrlParams(url);

            long maxRunningTime = getMaxRunningTime();

            RequestHandler handler;
            if ( params.containsKey( "lonlats" ) && params.containsKey( "profile" ) )
            {
            	handler = new ServerHandler( serviceContext, params );
            }
            else
            {
            	throw new IllegalArgumentException( "unknown request syntax: " + getline );
            }
            RoutingContext rc = handler.readRoutingContext();
            List<OsmNodeNamed> wplist = handler.readWayPointList();

            cr = new RoutingEngine( null, null, serviceContext.segmentDir, wplist, rc );
            cr.quite = true;
            cr.doRun( maxRunningTime );

            // http-header
            bw.write( "HTTP/1.1 200 OK\n" );
            bw.write( "Connection: close\n" );
            bw.write( "Content-Type: text/xml; charset=utf-8\n" );
            bw.write( "Access-Control-Allow-Origin: *\n" );
            bw.write( "\n" );

            if ( cr.getErrorMessage() != null )
            {
              bw.write( cr.getErrorMessage() );
              bw.write( "\n" );
            }
            else
            {
              OsmTrack track = cr.getFoundTrack();
              if ( track != null )
              {
                bw.write( handler.formatTrack(track) );
              }
            }
            bw.flush();
          }
          catch (Throwable e)
          {
             System.out.println("RouteServer got exception (will continue): "+e);
             e.printStackTrace();
          }
          finally
          {
              cr = null;
              if ( br != null ) try { br.close(); } catch( Exception e ) {}
              if ( bw != null ) try { bw.close(); } catch( Exception e ) {}
              if ( clientSocket != null ) try { clientSocket.close(); } catch( Exception e ) {}
          }
  }

  public static void main(String[] args) throws Exception
  {
        System.out.println("BRouter 0.9.8 / 12012014 / abrensch");
        if ( args.length != 4 )
        {
          System.out.println("serve BRouter protocol");
          System.out.println("usage: java RouteServer <segmentdir> <profiledir> <port> <maxthreads>");
          return;
        }

        ServiceContext serviceContext = new ServiceContext();
        serviceContext.segmentDir = args[0];
        System.setProperty( "profileBaseDir", args[1] );

        int maxthreads = Integer.parseInt( args[3] );

        TreeMap<Long,RouteServer> threadMap = new TreeMap<Long,RouteServer>();

        ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[2]));
        long last_ts = 0;
        for (;;)
        {
          Socket clientSocket = serverSocket.accept();
          RouteServer server = new RouteServer();
          server.serviceContext = serviceContext;
          server.clientSocket = clientSocket;

          // kill thread if limit reached
          if ( threadMap.size() >= maxthreads )
          {
             Long k = threadMap.firstKey();
             RouteServer victim = threadMap.get( k );
             threadMap.remove( k );
             victim.stopRouter();
          }

          long ts = System.currentTimeMillis();
          while ( ts <=  last_ts ) ts++;
          threadMap.put( Long.valueOf( ts ), server );
          last_ts = ts;
          server.start();
        }
  }


  private static HashMap<String,String> getUrlParams( String url )
  {
	  HashMap<String,String> params = new HashMap<String,String>();
	  StringTokenizer tk = new StringTokenizer( url, "?&" );
	  while( tk.hasMoreTokens() )
	  {
	    String t = tk.nextToken();
	    StringTokenizer tk2 = new StringTokenizer( t, "=" );
	    if ( tk2.hasMoreTokens() )
	    {
	      String key = tk2.nextToken();
	      if ( tk2.hasMoreTokens() )
	      {
	        String value = tk2.nextToken();
	        params.put( key, value );
	      }
	    }
	  }
	  return params;
  }

  private static long getMaxRunningTime() {
    long maxRunningTime = 60000;
    String sMaxRunningTime = System.getProperty( "maxRunningTime" );
    if ( sMaxRunningTime != null )
    {
      maxRunningTime = Integer.parseInt( sMaxRunningTime ) * 1000;
    }
    return maxRunningTime;
  }
}