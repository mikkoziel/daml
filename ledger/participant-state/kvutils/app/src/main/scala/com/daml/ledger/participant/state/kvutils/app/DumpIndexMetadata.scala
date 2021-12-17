// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state.kvutils.app

import com.daml.ledger.resources.{Resource, ResourceContext}
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.logging.LoggingContext.newLoggingContext
import com.daml.platform.server.api.validation.ErrorFactories
import com.daml.platform.store.IndexMetadata

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object DumpIndexMetadata {
  def apply(
      jdbcUrls: Seq[String],
      errorFactories: ErrorFactories,
      runnerName: String,
  )(implicit resourceContext: ResourceContext): Resource[Unit] = {
    val logger = ContextualizedLogger.get(this.getClass)
    import ExecutionContext.Implicits.global
    implicit val actorSystem: ActorSystem = ActorSystem(
      "[^A-Za-z0-9_\\-]".r.replaceAllIn(runnerName.toLowerCase, "-")
    )
    implicit val materializer: Materializer = Materializer(actorSystem)
    Resource.sequenceIgnoringValues(for (jdbcUrl <- jdbcUrls) yield {
      newLoggingContext { implicit loggingContext: LoggingContext =>
        Resource.fromFuture(IndexMetadata.read(jdbcUrl, errorFactories).andThen {
          case Failure(exception) =>
            logger.error("Error while retrieving the index metadata", exception)
          case Success(metadata) =>
            logger.warn(s"ledger_id: ${metadata.ledgerId}")
            logger.warn(s"participant_id: ${metadata.participantId}")
            logger.warn(s"ledger_end: ${metadata.ledgerEnd}")
            logger.warn(s"version: ${metadata.participantIntegrationApiVersion}")
        })
      }
    })
  }
}
