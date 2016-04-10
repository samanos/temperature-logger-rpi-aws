package io.github.samanos.tlog

import akka.Done
import akka.stream.scaladsl.Flow

import com.typesafe.config.Config

import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.io.ByteArrayInputStream
import java.security._
import java.security.cert._
import java.security.spec.PKCS8EncodedKeySpec
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.net.ssl._

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
   * Code humbly borrowed and adapted from:
   * https://github.com/awslabs/aws-iot-demo-for-danbo/blob/master/danbo-pi-client/src/main/java/com/amazonaws/services/iot/demo/danbo/rpi/SslUtil.java
   */
  def getSslSocketFactory(caCrt: String, clCrt: String, prKey: String) = {
    Security.addProvider(new BouncyCastleProvider());

    val caCert = CertificateFactory
      .getInstance("X.509")
      .generateCertificate(new ByteArrayInputStream(Base64.getDecoder.decode(caCrt)))
      .asInstanceOf[X509Certificate]

    val clCert = CertificateFactory
      .getInstance("X.509")
      .generateCertificate(new ByteArrayInputStream(Base64.getDecoder.decode(clCrt)))
      .asInstanceOf[X509Certificate]

    val privKey = KeyFactory
      .getInstance("RSA")
      .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(prKey)))
      .asInstanceOf[RSAPrivateKey]

		// CA certificate is used to authenticate server
    val caKs = KeyStore.getInstance(KeyStore.getDefaultType())
    caKs.load(null, null)
    caKs.setCertificateEntry("ca-certificate", caCert)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(caKs)

    // Client key and certificates are sent to server so it can authenticate us
		val ks = KeyStore.getInstance(KeyStore.getDefaultType())
    ks.load(null, null);
    ks.setCertificateEntry("certificate",	clCert)
    ks.setKeyEntry("private-key", privKey, "".toCharArray(), Array(clCert))

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, "".toCharArray());

    // Finally, create SSL socket factory
    val context = SSLContext.getInstance("TLSv1.2");
    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null)
    context.getSocketFactory()
  }
}
