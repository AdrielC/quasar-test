package io.quasar.blobstore
package v1

import scodec.bits.ByteVector
import com.google.protobuf.ByteString
import scalapb.TypeMapper

package object `package` {
  implicit val byteVectorTypeMapper: TypeMapper[ByteString, ByteVector] =
    TypeMapper[ByteString, ByteVector](bv => ByteVector.view(bv.asReadOnlyByteBuffer()))(bv => ByteString.copyFrom(bv.toByteBuffer))
}


