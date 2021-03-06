/**
 * Copyright 2012-2013 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stackmob.scaliak.linkwalk

import scalaz._
import Scalaz._
import com.basho.riak.client.query.LinkWalkStep.Accumulate
import com.stackmob.scaliak.{ScaliakConverter, ReadObject, ScaliakBucket}

sealed trait LinkWalkStep extends LinkWalkStepOperators {
  def bucket: String
  def tag: String
  def accumulate: Accumulate

  val existingSteps = this.wrapNel

  override def toString = List(bucket,tag,accumulate).mkString("LinkWalkStep(", ",", ")")

}

object LinkWalkStep {

  def apply(bucket: String, tag: String): LinkWalkStep = apply(bucket, tag, Accumulate.DEFAULT)

  def apply(bucket: String, tag: String, shouldAccumulate: Boolean): LinkWalkStep =
    if (shouldAccumulate) apply(bucket, tag, Accumulate.YES)
    else apply(bucket, tag, Accumulate.NO)

  def apply(b: String, t: String, a: Accumulate): LinkWalkStep = new LinkWalkStep {
    val bucket = b
    val tag = t
    val accumulate = a
  }

  implicit def LinkWalkStepEqual: Equal[LinkWalkStep] =
    Equal.equal((s1, s2) => s1.bucket === s2.bucket && s1.tag === s2.tag && s1.accumulate == s2.accumulate)
}

trait LinkWalkStepOperators {

  def existingSteps: LinkWalkSteps

  def -->(next: LinkWalkStep): LinkWalkSteps = step(next)
  def -->(nexts: LinkWalkSteps): LinkWalkSteps = step(nexts)
  def step(next: LinkWalkStep): LinkWalkSteps = step(next.wrapNel)
  def step(nexts: LinkWalkSteps): LinkWalkSteps = existingSteps |+| nexts

  def *(i: Int) = times(i)
  def times(i: Int): LinkWalkSteps =
    List.fill(i-1)(existingSteps).foldLeft(existingSteps)(_ |+| _)

}

class LinkWalkStepTuple3(value: (String, String, Boolean)) {
  def toLinkWalkStep = LinkWalkStep(value._1, value._2, value._3)
}

class LinkWalkStepTuple2(value: (String, String)) {
  def toLinkWalkStep = LinkWalkStep(value._1, value._2)
}

class LinkWalkStepsW(values: LinkWalkSteps) extends LinkWalkStepOperators {
  val existingSteps = values
}

class LinkWalkStartTuple(values: (ScaliakBucket, ReadObject)) {
  private val bucket = values._1
  private val obj = values._2

  def linkWalk[T](steps: LinkWalkSteps)(implicit converter: ScaliakConverter[T]) = {
    bucket.linkWalk(obj, steps)
  }
}