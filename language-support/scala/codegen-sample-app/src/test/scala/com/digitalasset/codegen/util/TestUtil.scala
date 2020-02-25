// Copyright (c) 2020 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.codegen.util

import java.io.File

import com.digitalasset.daml.bazeltools.BazelRunfiles._
import scalaz.{@@, Tag}

object TestUtil {
  sealed trait TestContextTag
  type TestContext = String @@ TestContextTag
  val TestContext: Tag.TagOf[TestContextTag] = Tag.of[TestContextTag]

  def requiredResource(path: String): File = {
    val f = new File(rlocation(path)).getAbsoluteFile
    require(f.exists, s"File does not exist: $f")
    f
  }
}
