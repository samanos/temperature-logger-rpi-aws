package io.github.samanos.tlog

import akka.Done

import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.cert.X509CertificateHolder

import java.io.StringReader
import java.security.Security
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import akka.stream.scaladsl.Flow

object Mqtt {

  def connection(conf: Config) = {
    val opts = new MqttConnectOptions()
    opts.setCleanSession(true)
    opts.setSocketFactory(getSslSocketFactory(conf.getString("ca-crt"), conf.getString("cl-crt"), conf.getString("private-key")))

    val client = new MqttClient(conf.getString("endpoint"), conf.getString("client-id"), new MemoryPersistence())
    client.connect(opts)

    val topic = conf.getString("topic")

    Flow[MqttMessage]
      .map { msg =>
        msg.setQos(0)
        client.publish(conf.getString("topic"), msg)
        Done
      }
      .viaMat(Flow[Done])((_, _) => client)
  }

  /**
   * Code humbly borrowed from:
   * https://github.com/awslabs/aws-iot-demo-for-danbo/blob/master/danbo-pi-client/src/main/java/com/amazonaws/services/iot/demo/danbo/rpi/SslUtil.java
   */
  def getSslSocketFactory(caCrt: String, clCrt: String, prKey: String) = {
    Security.addProvider(new BouncyCastleProvider());

    // load CA certificate
    val caCrtParser = new PEMParser(new StringReader(caCrt))
    val caCert = caCrtParser.readObject().asInstanceOf[X509CertificateHolder]
    caCrtParser.close

    // load client certificate
    val clCrtParser = new PEMParser(new StringReader(clCrt))
    val clCert = clCrtParser.readObject().asInstanceOf[X509CertificateHolder]
    clCrtParser.close

    // load client private key
    val prKeyParser = new PEMParser(new StringReader(prKey))
    val privKey = (new JcaPEMKeyConverter().setProvider("BC")).getKeyPair(prKeyParser.readObject().asInstanceOf[PEMKeyPair])
    prKeyParser.close

    val certConverter = new JcaX509CertificateConverter()
    certConverter.setProvider("BC")

		// CA certificate is used to authenticate server
    val caKs = KeyStore.getInstance(KeyStore.getDefaultType())
    caKs.load(null, null)
    caKs.setCertificateEntry("ca-certificate", certConverter.getCertificate(caCert))

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(caKs)

    // Client key and certificates are sent to server so it can authenticate us
		val ks = KeyStore.getInstance(KeyStore.getDefaultType())
    ks.load(null, null);
    ks.setCertificateEntry("certificate",	certConverter.getCertificate(clCert))
    ks.setKeyEntry("private-key", privKey.getPrivate(), "".toCharArray(), Array(certConverter.getCertificate(clCert)))

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, "".toCharArray());

    // Finally, create SSL socket factory
    val context = SSLContext.getInstance("TLSv1.2");
    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null)
    context.getSocketFactory()
  }
}
