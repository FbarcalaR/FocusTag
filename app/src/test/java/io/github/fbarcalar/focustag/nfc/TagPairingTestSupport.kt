package io.github.fbarcalar.focustag.nfc

/** The id written on a [TagProof.WrittenId] tag; tests use it to build matching URIs. */
val TagPairing.tagId: String get() = (proof as TagProof.WrittenId).tagId

/** A copy of this written pairing carrying [tagId] instead. */
fun TagPairing.withTagId(tagId: String): TagPairing = copy(proof = TagProof.WrittenId(tagId))
