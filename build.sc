import $file.deps, deps.{Deps, MingwCommands, Scala, WindowsJvm}
import $file.settings, settings.{GenerateHeaders, HasCSources, JniUtilsPublishModule, downloadWindowsJvmArchive, isWindows, unpackWindowsJvmArchive}

import mill._, scalalib._

import scala.concurrent.duration._


object `windows-jni-utils` extends WindowsUtils with HasCSources with JniUtilsPublishModule {
  def linkingLibs = Seq("ole32")
  def dllName = "csjniutils"

  def compile = T{
    headers.`windows-jni-utils`.compile()
    super.compile()
  }

  def msysShell = MingwCommands.msysShell
  def gcc = MingwCommands.gcc
  def windowsJavaHome = sharedWindowsJavaHome()
}

object `windows-jni-utils-tests` extends ScalaModule with JniUtilsPublishModule {
  def scalaVersion = Scala.scala213
  def moduleDeps = Seq(
    `windows-jni-utils`
  )
  object test extends Tests {
    def ivyDeps = Agg(
      Deps.utest
    )
    def testFrameworks = Seq("utest.runner.Framework")
  }
}

// compile these projects to generate or update JNI header files
object headers extends Module {
  object `windows-jni-utils` extends WindowsUtils with GenerateHeaders

  implicit def millModuleBasePath: define.BasePath =
    define.BasePath(super.millModuleBasePath.value / os.up)
}

trait WindowsUtils extends MavenModule  {
  def compileIvyDeps = Agg(
    Deps.svm
  )
}

def windowsJvmArchive = T.persistent {
  downloadWindowsJvmArchive(WindowsJvm.url, WindowsJvm.archiveName)
}

def sharedWindowsJavaHome: T[String] =
  if (isWindows)
    T{
      import java.io.File
      val value = sys.props("java.home")
      val dir = new File(value)
      // Seems required with Java 8
      if (dir.getName == "jre") dir.getParent
      else value
    }
  else
    T.persistent {
      // On Linux / macOS, get a Windows JDK for the Windows JNI header files
      val windowsJvmArchive0 = windowsJvmArchive()
      unpackWindowsJvmArchive(windowsJvmArchive0.path, WindowsJvm.archiveName).toString
    }

def publishSonatype(tasks: mill.main.Tasks[PublishModule.PublishData]) =
  T.command {
    val timeout = 10.minutes
    val credentials = sys.env("SONATYPE_USERNAME") + ":" + sys.env("SONATYPE_PASSWORD")
    val pgpPassword = sys.env("PGP_PASSWORD")
    val data = define.Task.sequence(tasks.value)()

    settings.publishSonatype(
      credentials = credentials,
      pgpPassword = pgpPassword,
      data = data,
      timeout = timeout,
      log = T.ctx().log
    )
  }
