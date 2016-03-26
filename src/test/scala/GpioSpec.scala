package io.github.samanos.tlog

import better.files._, Cmds._
import com.typesafe.config.ConfigFactory
import org.scalatest._

class GpioSpec extends WordSpec with Matchers {

  "gpio" should {

    "export" when {

      "gpio port is not exported" in withTempDirectory { tempDir =>
        val conf = config(s"tlog.gpio=$tempDir")
        val gpio = Gpio(conf)

        gpio.export(Gpio.Port.Gpio17)

        (tempDir / "export").contentAsString.trim shouldBe "17"
      }
    }

    "not export" when {

      "gpio port is exported" in withTempDirectory { tempDir =>
        val conf = config(s"tlog.gpio=$tempDir")
        val gpio = Gpio(conf)

        (tempDir / "gpio17").createIfNotExists()
        gpio.export(Gpio.Port.Gpio17)

        (tempDir / "export").notExists shouldBe true
      }

    }

    "change direction" in withTempDirectory { tempDir =>
      val conf = config(s"tlog.gpio=$tempDir")
      val gpio = Gpio(conf)

      (tempDir / "gpio17" / "direction").createIfNotExists() << "out"
      gpio.direction(Gpio.Port.Gpio17, Gpio.Direction.In)

      (tempDir / "gpio17" / "direction").contentAsString.trim shouldBe "in"
    }

    "change value" in withTempDirectory { tempDir =>
      val conf = config(s"tlog.gpio=$tempDir")
      val gpio = Gpio(conf)

      (tempDir / "gpio17" / "value").createIfNotExists() << "1"
      gpio.value(Gpio.Port.Gpio17, "0")

      (tempDir / "gpio17" / "value").contentAsString.trim shouldBe "0"
    }

    "get temperature" in withTempDirectory { tempDir =>
      (tempDir / "28-000005d99910" / "w1_slave").createIfNotExists() << "70 01 4b 46 7f ff 10 10 e1 : crc=e1 YES\n70 01 4b 46 7f ff 10 10 e1 t=23000"
      (tempDir / "28-0000067aba63" / "w1_slave").createIfNotExists() << "60 01 4b 46 7f ff 10 10 b5 : crc=b5 YES\n60 01 4b 46 7f ff 10 10 b5 t=22000"
      (tempDir / "sensors").createDirectory()
      ln_s(tempDir / "sensors" / "28-000005d99910", tempDir / "28-000005d99910")
      ln_s(tempDir / "sensors" / "28-0000067aba63", tempDir / "28-0000067aba63")

      val conf = config(s"tlog.w1=${tempDir}/sensors")
      val gpio = Gpio(conf)

      gpio.temperature.toSet shouldBe Set(23.000, 22.000)
    }

  }

  def withTempDirectory(test: (File) => Any) {
    val tempDir = File.newTemporaryDirectory()
    try {
      test(tempDir)
    } finally {
      tempDir.delete()
    }
  }

  def config(conf: String) =
    ConfigFactory.parseString(conf).withFallback(ConfigFactory.load())
}
