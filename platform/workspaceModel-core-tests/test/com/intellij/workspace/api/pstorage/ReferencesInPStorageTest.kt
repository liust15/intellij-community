// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import com.intellij.workspace.api.pstorage.entities.*
import com.intellij.workspace.api.pstorage.entities.ModifiablePChildEntity
import com.intellij.workspace.api.pstorage.entities.ModifiablePChildWithOptionalParentEntity
import com.intellij.workspace.api.pstorage.entities.ModifiablePParentEntity
import com.intellij.workspace.api.pstorage.entities.PChildEntity
import com.intellij.workspace.api.pstorage.entities.PChildWithOptionalParentEntity
import com.intellij.workspace.api.pstorage.entities.PDataClass
import com.intellij.workspace.api.pstorage.entities.PParentEntity
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun TypedEntityStorage.singlePParent() = entities(PParentEntity::class.java).single()

private fun TypedEntityStorage.singlePChild() = entities(PChildEntity::class.java).single()

class ReferencesInPStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity(builder.addPParentEntity("foo"))
    builder.assertConsistency()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singlePChild())
    assertEquals(child.parent, builder.singlePParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = PEntityStorageBuilder.create()
    val parentEntity = builder.addPParentEntity("foo")

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    diff.addPChildEntity(parentEntity = parentEntity)
    builder.addDiff(diff)
    builder.assertConsistency()

    val child = builder.singlePChild()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singlePChild())
    assertEquals(child.parent, builder.singlePParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = PEntityStorageBuilder.create()
    val parent1 = builder.addPParentEntity("parent1")
    val parent2 = builder.addPParentEntity("parent2")
    builder.assertConsistency()
    val child = builder.addPChildEntity(parent1, "child", PDataClass("data", builder.createReference(parent2)))
    builder.assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList<PChildEntity>(), parent2.children.toList())
    assertEquals("parent1", child.parent.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder).parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(PParentEntity::class.java).toSet())

    builder.modifyEntity(ModifiablePChildEntity::class.java, child) {
      dataClass = null
    }
    builder.assertConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(PParentEntity::class.java).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity()
    builder.assertConsistency()
    val child = builder.addPChildEntity(parent)
    builder.assertConsistency()
    builder.removeEntity(child)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(emptyList<PChildEntity>(), parent.children.toList())
    assertEquals(parent, builder.singlePParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()
    builder.removeEntity(child.parent)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(emptyList<PParentEntity>(), builder.entities(PParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity via diff`() {
    val builder = PEntityStorageBuilder.create()
    val oldParent = builder.addPParentEntity("oldParent")
    val oldChild = builder.addPChildEntity(oldParent, "oldChild")
    val diff = PEntityStorageBuilder.create()
    val parent = diff.addPParentEntity("newParent")
    diff.addPChildEntity(parent, "newChild")
    diff.removeEntity(parent)
    diff.assertConsistency()
    builder.addDiff(diff)
    builder.assertConsistency()
    assertEquals(listOf(oldChild), builder.entities(PChildEntity::class.java).toList())
    assertEquals(listOf(oldParent), builder.entities(PParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = PEntityStorageBuilder.create()
    val child1 = builder.addPChildEntity()
    builder.addPChildEntity(parentEntity = child1.parent)
    builder.removeEntity(child1.parent)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(emptyList<PParentEntity>(), builder.entities(PParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity()
    val child = builder.addPChildEntity(parentEntity = parent)
    builder.addPChildChildEntity(parent, child)
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(emptyList<PParentEntity>(), builder.entities(PParentEntity::class.java).toList())
  }

  // UNSUPPORTED
/*
  @Test
  fun `remove parent entity referenced via data class`() {
    val builder = PEntityStorageBuilder.create()
    val parent1 = builder.addPParentEntity("parent1")
    val parent2 = builder.addPParentEntity("parent2")
    builder.addPChildEntity(parent1, "child", PDataClass("data", builder.createReference(parent2)))
    builder.removeEntity(parent2)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(listOf(parent1), builder.entities(PParentEntity::class.java).toList())
    assertEquals(emptyList<PChildEntity>(), parent1.children.toList())
  }
*/

  @Test
  fun `remove parent entity referenced via two paths`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity()
    builder.addPChildEntity(parent, "child", PDataClass("data", builder.createReference(parent)))
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(emptyList<PParentEntity>(), builder.entities(PParentEntity::class.java).toList())
  }

  @Test
  fun `modify parent property`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()
    val oldParent = child.parent
    val newParent = builder.modifyEntity(ModifiablePParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singlePParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify parent property via diff`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()
    val oldParent = child.parent

    val diff = PEntityStorageBuilder.from(builder)
    diff.modifyEntity(ModifiablePParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.addDiff(diff)
    builder.assertConsistency()
    val newParent = builder.singlePParent()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singlePParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify child property`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()
    val oldParent = child.parent
    val newChild = builder.modifyEntity(ModifiablePChildEntity::class.java, child) {
      childProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newChild.childProperty)
    assertEquals(oldParent, builder.singlePParent())
    assertEquals(newChild, builder.singlePChild())
    assertEquals(oldParent, newChild.parent)
    assertEquals(oldParent, child.parent)
    assertEquals(newChild, oldParent.children.single())
    assertEquals("child", child.childProperty)
  }

  @Test
  fun `modify reference to parent`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()
    val oldParent = child.parent
    val newParent = builder.addPParentEntity("new")
    val newChild = builder.modifyEntity(ModifiablePChildEntity::class.java, child) {
      parent = newParent
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals(setOf(oldParent, newParent), builder.entities(PParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singlePChild())
    assertEquals(newParent, newChild.parent)
    assertEquals(newChild, newParent.children.single())

    assertEquals(newParent, child.parent)
    //assertEquals(oldParent, child.parent)  // ProxyBasedStore behaviour

    assertEquals(emptyList<PChildEntity>(), oldParent.children.toList())
  }

  @Test
  fun `modify reference to parent via data class`() {
    val builder = PEntityStorageBuilder.create()
    val parent1 = builder.addPParentEntity("parent1")
    val oldParent = builder.addPParentEntity("parent2")
    val child = builder.addPChildEntity(parent1, "child", PDataClass("data", builder.createReference(oldParent)))
    val newParent = builder.addPParentEntity("new")
    builder.assertConsistency()
    val newChild = builder.modifyEntity(ModifiablePChildEntity::class.java, child) {
      dataClass = PDataClass("data2", builder.createReference(newParent))
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(PParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singlePChild())
    assertEquals(newParent, newChild.dataClass.parent.resolve(builder))
    assertEquals(oldParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `builder from storage`() {
    val storage = PEntityStorageBuilder.create().apply {
      addPChildEntity()
    }.toStorage()
    storage.assertConsistency()

    assertEquals("parent", storage.singlePParent().parentProperty)

    val builder = PEntityStorageBuilder.from(storage)
    builder.assertConsistency()

    val oldParent = builder.singlePParent()
    assertEquals("parent", oldParent.parentProperty)
    val newParent = builder.modifyEntity(ModifiablePParentEntity::class.java, oldParent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singlePParent().parentProperty)
    assertEquals("parent", storage.singlePParent().parentProperty)
    assertEquals(newParent, builder.singlePChild().parent)
    assertEquals("changed", builder.singlePChild().parent.parentProperty)
    assertEquals("parent", storage.singlePChild().parent.parentProperty)

    val parent2 = builder.addPParentEntity("parent2")
    builder.modifyEntity(ModifiablePChildEntity::class.java, builder.singlePChild()) {
      dataClass = PDataClass("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", storage.singlePParent().parentProperty)
    assertEquals(null, storage.singlePChild().dataClass)
    assertEquals("data", builder.singlePChild().dataClass!!.stringProperty)
    assertEquals(parent2, builder.singlePChild().dataClass!!.parent.resolve(builder))
    assertEquals(setOf(parent2, newParent), builder.entities(PParentEntity::class.java).toSet())
  }

  @Test
  fun `storage from builder`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildEntity()

    val snapshot = builder.toStorage()
    builder.assertConsistency()

    builder.modifyEntity(ModifiablePParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singlePParent().parentProperty)
    assertEquals("changed", builder.singlePChild().parent.parentProperty)
    assertEquals("parent", snapshot.singlePParent().parentProperty)
    assertEquals("parent", snapshot.singlePChild().parent.parentProperty)

    val parent2 = builder.addPParentEntity("new")
    builder.modifyEntity(ModifiablePChildEntity::class.java, child) {
      dataClass = PDataClass("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", snapshot.singlePParent().parentProperty)
    assertEquals(null, snapshot.singlePChild().dataClass)
    assertEquals(parent2, builder.singlePChild().dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify optional parent property`() {
    val builder = PEntityStorageBuilder.create()
    val child = builder.addPChildWithOptionalParentEntity(null)
    Assert.assertNull(child.optionalParent)
    val newParent = builder.addPParentEntity()
    assertEquals(emptyList<PChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
    val newChild = builder.modifyEntity(ModifiablePChildWithOptionalParentEntity::class.java, child) {
      optionalParent = newParent
    }
    builder.assertConsistency()
    assertEquals(newParent, newChild.optionalParent)
    assertEquals(newChild, newParent.optionalChildren.single())

    val veryNewChild = builder.modifyEntity(ModifiablePChildWithOptionalParentEntity::class.java, newChild) {
      optionalParent = null
    }
    Assert.assertNull(veryNewChild.optionalParent)
    assertEquals(emptyList<PChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
  }
}
