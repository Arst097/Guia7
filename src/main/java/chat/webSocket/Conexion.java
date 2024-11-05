
package chat.webSocket;

import java.io.IOException;
import java.util.Set;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


//Manejamos anotaciones para que a partir de una unica clase se hagan todos los requisitos
//Cada chat o endpoint se ejecutará en un hilo.
//Cada hilo se seguira ejecutando mientras el participante siga en el chat.
@ServerEndpoint(value="/websocket/chat") //Esta direccion usara el cliente para unirse al chat

public class Conexion {
    
    //Aqui pondremos las caracteristicas que compartiran todos los chats
    //Bitacora de las entradas de sesion
    private static final Log Log = LogFactory.getLog(Conexion.class);
   
    //Identificador de cada participante del chat
    private static final String PREFIJO_PARTICIPANTE = "User Nro. ";
    
    /*Es el nro. identificador de cada usuario. Que sea AtomicInteger asegura que no haya un colapso
    si dos sesiones son iniciadas al mismo tiempo. Maneja dicha concurrencia.*/
    private static final AtomicInteger idConexion = new AtomicInteger(1);
    
    /*Luego tendremos la coleccion de conexiones con Set usando CopyOnWriteArraySet,
    lo cual maneja la concurrencia de los diferentes hilos o chats ejecutados ademas
    de notificar a los demas hilos sobre sus aportes o cambios*/
    private static final Set<Conexion> conexiones = new CopyOnWriteArraySet<>();
    
    
    private final String nickname;
    private Session sesionWebSocket;

    //Aqui por cada sesion se genera el nickname con id incremental
    public Conexion(String nickname) {
        this.nickname = PREFIJO_PARTICIPANTE + idConexion.getAndIncrement();
    }
    
    //Se ejecutara mientras siga en pie la conexion lo siguiente:
    
    /*Se agrega la conexion a la lista conexiones con determinados atributos,
    los cuales seran utilizados durante la conexion*/
    @OnOpen
    public void iniciarConexion(Session sesion){
        this.sesionWebSocket = sesion;
        conexiones.add(this);
        String mensajeIni = String.format("El %s %s",nickname,"se ha *unido* al chat.");
        multicast(mensajeIni);
    }
    
    //Se elimina la conexion de la lista conexiones y se cierra
    @OnClose
    public void terminarConexion(){
        conexiones.remove(this);
        String mensajeFinal = String.format("El %s %s",nickname,"*salio* del chat");
        multicast(mensajeFinal);
    }
    
    //Al momento de llegar un mensaje al servidor, este de la formato y lo envia a todos
    @OnMessage
    public void atenderMensaje(String mensaje){
        String mensajeConID = String.format("%s: %s",nickname,mensaje);
        multicast(mensajeConID);
    }
    
    //Al momento de llegar un error de conexion se lanzara una excepcion y se guardara en Log
    @OnError
    public void onError(Throwable t) throws Throwable{
        Log.error("Chat Error: " + t.toString(), t);
    }
    
    //El metodo para publicar los cambios en los chats
    private static void multicast(String msj){
        //Itera sobre la coleccion de conexiones abiertas
        for(Conexion i : conexiones){
            try {
                //Synchronized maneja la concurrencia respecto al envio de mensajes por cada sesion
                synchronized(i){
                    if(i.sesionWebSocket.isOpen()){
                        i.sesionWebSocket.getBasicRemote().sendText(msj);
                        //Se envia el mensaje para cada sesion activa.
                    }
                }
            } catch (IOException e) {
                Log.debug("Error en el chat: Falló actualización de mensajes",e);
            }
        }
    }
}
