// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.testtool

import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}

import com.daml.ledger.api.testtool.infrastructure.Reporter.ColorizedPrintStreamReporter
import com.daml.ledger.api.testtool.infrastructure.{
  LedgerSessionConfiguration,
  LedgerTestSuiteRunner,
  LedgerTestSummary
}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object LedgerApiTestTool {

  private[this] val logger = LoggerFactory.getLogger(getClass.getName.stripSuffix("$"))

  private[this] val uncaughtExceptionErrorMessage =
    "UNEXPECTED UNCAUGHT EXCEPTION ON MAIN THREAD, GATHER THE STACKTRACE AND OPEN A _DETAILED_ TICKET DESCRIBING THE ISSUE HERE: https://github.com/digital-asset/daml/issues/new"

  private def exitCode(summaries: Vector[LedgerTestSummary], expectFailure: Boolean): Int =
    if (summaries.exists(_.result.failure) == expectFailure) 0 else 1

  private def printAvailableTests(): Unit = {
    println("Tests marked with * are run by default.\n")
    tests.default.keySet.toSeq.sorted.map(_ + " *").foreach(println(_))
    tests.optional.keySet.toSeq.sorted.foreach(println(_))
  }

  private def extractResources(resources: String*): Unit = {
    val pwd = Paths.get(".").toAbsolutePath
    println(s"Extracting all DAML resources necessary to run the tests into $pwd.")
    for (resource <- resources) {
      val is = getClass.getResourceAsStream(resource)
      if (is == null) sys.error(s"Could not find $resource in classpath")
      val targetFile = new File(new File(resource).getName)
      Files.copy(is, targetFile.toPath, StandardCopyOption.REPLACE_EXISTING)
      println(s"Extracted $resource to $targetFile")
    }
  }

  def main(args: Array[String]): Unit = {

    val config = Cli.parse(args).getOrElse(sys.exit(1))

    if (config.listTests) {
      printAvailableTests()
      sys.exit(0)
    }

    if (config.extract) {
      extractResources(
        "/ledger/test-common/SemanticTests.dar",
        "/ledger/test-common/Test.dar",
        "/ledger/test-common/Test-1.6.dar"
      )
      sys.exit(0)
    }

    val included =
      if (config.allTests) tests.all.keySet
      else if (config.included.isEmpty) tests.default.keySet
      else config.included

    val testsToRun = tests.all.filterKeys(included -- config.excluded)

    if (testsToRun.isEmpty) {
      println("No tests to run.")
      sys.exit(0)
    }

    Thread
      .currentThread()
      .setUncaughtExceptionHandler((_, exception) => {
        logger.error(uncaughtExceptionErrorMessage, exception)
        sys.exit(1)
      })

    val runner = new LedgerTestSuiteRunner(
      Vector(
        LedgerSessionConfiguration(
          config.host,
          config.port,
          config.tlsConfig,
          config.commandSubmissionTtlScaleFactor)),
      testsToRun.values.toVector,
      config.timeoutScaleFactor
    )

    runner.run {
      case Success(summaries) =>
        new ColorizedPrintStreamReporter(System.out, config.verbose).report(summaries)
        sys.exit(exitCode(summaries, config.mustFail))
      case Failure(e) =>
        logger.error(e.getMessage, e)
        sys.exit(1)
    }
  }

}
