// imports

import java.io._
import java.nio.file._
import StandardCopyOption._

import scala.io.Codec.UTF8

// task and setting definitions

organization := "com.mchange"

name := "solcJ-compat"

version := "0.4.18rev1-SNAPSHOT"

autoScalaLibrary := false // this is a pure Java library, don't depend on Scala

crossPaths := false //don't include _<scala-version> in artifact names

noSuffixVersion := {
  val fullVersion = version.value
  val NoSuffixRegex( out ) = fullVersion
  out
}

versionsDir in Compile := (sourceDirectory in Compile).value / "versions"

generateSolcJResources in Compile := {
  val genResourcesDir    = (resourceManaged in Compile ).value
  val allVersionsDir     = (versionsDir in Compile).value
  val nsv                = noSuffixVersion.value
  val specificVersionDir = new File( allVersionsDir, nsv )

  def macWinLinux( parentDir : File, make : Boolean ) : ( File, File, File ) = {
    val nativeDir          = new File( parentDir, "native" )
    val Seq( macDir, winDir, linuxDir ) = {
      val out = Seq( "mac", "win", "linux" ).map( new File( nativeDir, _ ) ).map( new File( _, "solc" ) )
      if (make) out.foreach( _.mkdirs )
      out
    }
    ( macDir, winDir, linuxDir )
  }
  val (srcMac, srcWin, srcLinux)     = macWinLinux( specificVersionDir, false )
  val (destMac, destWin, destLinux ) = macWinLinux( genResourcesDir, true )

  val copiedFiles = forceCopyContents( specificVersionDir, genResourcesDir )
  val macFileList = writeFileList(   srcMac,   destMac )
  val winFileList = writeFileList(   srcWin,   destWin )
  val linFileList = writeFileList( srcLinux, destLinux )

  copiedFiles :+ macFileList :+ winFileList :+ linFileList
}

resourceGenerators in Compile += (generateSolcJResources in Compile).taskValue

generateVersionClass in Compile := {
  val sm  = (sourceManaged in Compile).value
  val nsv = noSuffixVersion.value
  val gensrc = {
    s"""|package org.ethereum.solcJ;
        |
        |// modified from https://github.com/ether-camp/solcJ/blob/756e630daf7dcb7d04c31afd0331eea7dcc05615/src/main/java/org/ethereum/solcJ/SolcVersion.java
        |
        |/**
        | * Created by Stan Reshetnyk on 31.10.16.
        | */
        |public class SolcVersion {
        |
        |    public static final String VERSION = "${nsv}";
        |}""".stripMargin
  }
  val subdir = sm / "org" / "ethereum" / "solcJ"
  subdir.mkdirs()
  val file = new File( subdir, "SolcVersion.java" )
  IO.write( file, gensrc )
  Seq( file )
}

sourceGenerators in Compile += (generateVersionClass in Compile).taskValue

resolvers += ("releases" at nexusReleases)

resolvers += ("snapshots" at nexusSnapshots)

publishTo := {
  val v = version.value
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexusSnapshots )
  }
  else {
    Some("releases"  at nexusReleases )
  }
}

pomExtra := {
  val projectName = name.value

    <url>https://github.com/swaldman/{projectName}</url>
    <licenses>
      <license>
        <name>GNU General Public License, Version 3</name>
        <url>http://www.gnu.org/licenses/gpl-3.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:swaldman/{projectName}.git</url>
      <connection>scm:git:git@github.com:swaldman/{projectName}</connection>
    </scm>
    <developers>
      <developer>
        <id>swaldman</id>
        <name>Steve Waldman</name>
        <email>swaldman@mchange.com</email>
      </developer>
    </developers>
}
// new task and setting declarations

val noSuffixVersion = settingKey[String]("The version without any trailing 'snapshot' or other suffix.")

val versionsDir = settingKey[File]("Directory where different-version binaries are kept.")

val generateSolcJResources = taskKey[Seq[File]]("Generates the resources that become included in the solcJ-compat jar.")

val generateVersionClass = taskKey[Seq[File]]("Generates the class that reports the solidty compiler (no-suffix) version in a JVM accessible way.")

// implementation definitions

val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

val NoSuffixRegex = """(^\d+\.\d+\.\d+)(?:\S+)?$""".r

def fileListAsSeq( prefixFile : File ) : Seq[String] = {
  require( prefixFile.isDirectory )
  val prefixAbsPath = prefixFile.getAbsolutePath 
  val normalizedPrefixFile = if ( prefixAbsPath endsWith File.pathSeparator ) prefixFile else new File( prefixAbsPath + File.pathSeparator )
  val npfLen = normalizedPrefixFile.getAbsolutePath.length
  ( prefixFile ** "*" ).get.filterNot( _.isDirectory ).map( _.getAbsolutePath ).map( _.drop( npfLen ) )
}

def writeFileList( origParentDir : File, newParentDir : File ) : File = {
  val outFile = new File( newParentDir, "file.list" )
  val pw = new PrintWriter( new OutputStreamWriter( new BufferedOutputStream( new FileOutputStream( outFile ) ), UTF8.charSet ) )
  try fileListAsSeq( origParentDir ).foreach( pw.println ) finally pw.close()
  outFile
}

def forceCopyContents( origParentDir : File, newParentDir : File ) : Seq[File] = {
  fileListAsSeq( origParentDir ) map { relativePath =>
    val srcFile = new File( origParentDir, relativePath )
    val destFile = new File( newParentDir, relativePath )
    Files.copy( srcFile.toPath, destFile.toPath, REPLACE_EXISTING )
    destFile
  }
}
