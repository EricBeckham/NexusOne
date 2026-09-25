package one.nexus.p2.idp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Test
    void treatsSpecialPurposeIpv4AsNonPublic() throws Exception {
        for (String address : new String[] {"0.1.2.3", "192.0.0.8", "198.18.0.1", "198.19.255.254",
                "240.0.0.1", "255.255.255.255"}) {
            assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName(address)), address);
        }
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("198.20.0.1")));
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("192.0.1.1")));
    }

    @Test
    void checksTheIpv4InsideNat64SixToFourAndCompatibleIpv6() throws Exception {
        for (String address : new String[] {"64:ff9b::a9fe:a9fe", "64:ff9b::7f00:1", "2002:0a00:0001::1",
                "2002:a9fe:a9fe::", "::a00:1", "64:ff9b:1::cb00:7105", "2001:0:4136:e378::1"}) {
            assertTrue(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName(address)), address);
        }
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("64:ff9b::cb00:7105")));
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("2002:cb00:7105::1")));
        assertFalse(OidcClientCredentialsTester.isNonPublic(InetAddress.getByName("2606:4700::1111")));
    }

    @Test
    void formUrlEncodesSecretBytesTheSameWayAsTheFormEncoder() {
        byte[] secret = "p@ss word/字*ok".getBytes(StandardCharsets.UTF_8);
        String expected = URLEncoder.encode(new String(secret, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertEquals(expected, new String(OidcClientCredentialsTester.formUrlEncode(secret), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAPrivateIssuerWithoutCallingTheNetwork() {
        var tester = new OidcClientCredentialsTester(new tools.jackson.databind.ObjectMapper());
        var provider = new one.nexus.p2.domain.Model.IdpConfiguration(
                null, null, "OIDC", "local", "client", "https://127.0.0.1/realms/demo",
                "ACTIVE", null, null, 0, null, null, null, 1);
        assertFalse(tester.test(provider, "secret".getBytes(StandardCharsets.UTF_8)));
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
