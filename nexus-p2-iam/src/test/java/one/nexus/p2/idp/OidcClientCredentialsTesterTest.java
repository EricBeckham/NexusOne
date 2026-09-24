package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

class OidcClientCredentialsTesterTest {

    @Test
    void rejectsTheConnectedPeerWhenItIsNotPublic() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                Socket client = new Socket()) {
            client.connect(server.getLocalSocketAddress(), 1_000);
            try (Socket accepted = server.accept()) {
                AtomicBoolean delegated = new AtomicBoolean();
                var factory = new OidcClientCredentialsTester.PublicPeerSslSocketFactory(recordingFactory(delegated));
                IOException failure = assertThrows(IOException.class,
                        () -> factory.createSocket(client, "id.example.test", 443, true));

                assertEquals("connection target is not a public address", failure.getMessage());
                assertFalse(failure.getMessage().contains("127.0.0.1"));
                assertTrue(client.isClosed());
                assertFalse(delegated.get());
                assertTrue(accepted.isConnected());
            }
        }
    }

    @Test
    void acceptsAConnectedPublicPeer() throws Exception {
        Socket peer = new Socket() {
            @Override
            public InetAddress getInetAddress() {
                try {
                    return InetAddress.getByName("203.0.113.5");
                } catch (IOException ex) {
                    return null;
                }
            }
        };
        AtomicBoolean delegated = new AtomicBoolean();
        var factory = new OidcClientCredentialsTester.PublicPeerSslSocketFactory(recordingFactory(delegated));

        Socket secured = factory.createSocket(peer, "id.example.test", 443, true);

        assertTrue(delegated.get());
        assertFalse(secured.isClosed());
        assertFalse(OidcClientCredentialsTester.isNonPublic(secured.getInetAddress()));
    }

    @Test
    void treatsLoopbackLinkLocalSiteLocalAndUniqueLocalAsNonPublic() throws Exception {
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("127.0.0.1")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("10.1.2.3")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("192.168.1.1")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("172.16.0.1")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("169.254.169.254")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("100.64.0.1")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("0.0.0.0")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("::1")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("fd00::1")));
        byte[] mappedPrivate = new byte[16];
        mappedPrivate[10] = (byte) 0xff;
        mappedPrivate[11] = (byte) 0xff;
        mappedPrivate[12] = 10;
        mappedPrivate[15] = 1;
        assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByAddress(mappedPrivate)));
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("203.0.113.5")));
        assertTrue(OidcClientCredentialsTester.isNonPublic(null));
    }

    private static SSLSocketFactory recordingFactory(AtomicBoolean delegated) {
        return new SSLSocketFactory() {
            @Override
            public String[] getDefaultCipherSuites() {
                return new String[0];
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return new String[0];
            }

            @Override
            public Socket createSocket(Socket socket, String host, int port, boolean autoClose) {
                delegated.set(true);
                return socket;
            }

            @Override
            public Socket createSocket(String host, int port) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Socket createSocket(InetAddress host, int port) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
