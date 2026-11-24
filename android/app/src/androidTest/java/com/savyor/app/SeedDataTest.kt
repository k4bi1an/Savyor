package com.savyor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.KeyMomentWithImage
import com.savyor.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SeedDataTest {

    @Test
    fun seedSampleReelsForScreenshots(): Unit {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
        val db = SavyorDatabase.getInstance(context)
        val gson = Gson()

        // 1. Reel 1: Tech & Code
        val sc1 = "DdKXX_mTXLx"
        val dir1 = File(context.filesDir, "snapshots/$sc1").apply { mkdirs() }
        val thumb1 = File(dir1, "thumb.webp").apply { createPlaceholderWebp(this, "POSTGRES\nVECTOR DB", Color.DKGRAY) }
        val moment1_1 = File(dir1, "moment_0.webp").apply { createPlaceholderWebp(this, "Terminal\n$ docker run", Color.BLACK) }
        val moment1_2 = File(dir1, "moment_1.webp").apply { createPlaceholderWebp(this, "SQL Schema\nCREATE EXTENSION", Color.rgb(30, 45, 60)) }

        val reel1 = ReelEntity(
            shortcode = sc1,
            originalUrl = "https://www.instagram.com/reel/$sc1/",
            cleanUrl = "https://www.instagram.com/reel/$sc1/",
            creator = "tech_bytes",
            sourceLanguage = "English",
            title = "Spin Up a Local PostgreSQL Vector DB with pgvector in 1 Command",
            summary = "Execute docker run with pgvector/pgvector:pg16 to launch a local PostgreSQL database with vector indexing pre-configured on port 5432.",
            category = "Tech & Code",
            tags = gson.toJson(listOf("postgres", "docker", "pgvector", "rag")),
            transcript = "[0:00] Stop installing Postgres manually for vector search. [0:04] Run this single docker command to get pgvector running on port 5432. [0:12] Connect with psql and enable the vector extension.",
            keyTakeaways = gson.toJson(listOf(
                "Image pgvector/pgvector:pg16 includes PostgreSQL 16 and HNSW/IVFFlat vector extensions.",
                "Exposes port 5432 with password in POSTGRES_PASSWORD env var.",
                "Ideal for local RAG embedding development without cloud costs."
            )),
            keyMoments = gson.toJson(listOf(
                KeyMomentWithImage(2, "Terminal docker run execution", moment1_1.absolutePath),
                KeyMomentWithImage(8, "pgvector extension verification", moment1_2.absolutePath)
            )),
            structuredContent = gson.toJson(StructuredContent(
                noteType = "code",
                commands = listOf(
                    "docker run -d --name pgvector-local -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 pgvector/pgvector:pg16"
                ),
                codeSnippets = listOf(
                    "CREATE EXTENSION IF NOT EXISTS vector;\nCREATE TABLE items (id bigserial PRIMARY KEY, embedding vector(1536));"
                )
            )),
            extractedResources = gson.toJson(listOf(
                ExtractedResource("pgvector Docker Hub", "https://hub.docker.com/r/pgvector/pgvector", "Official pre-built docker image")
            )),
            caveats = gson.toJson(listOf("Requires Docker daemon running locally")),
            useWhen = "Use when testing RAG embeddings locally without cloud database bills.",
            thumbnailPath = thumb1.absolutePath,
            status = "UNREAD",
            processingState = "COMPLETED"
        )

        // 2. Reel 2: Recipes & Cooking (Multilingual Tamil)
        val sc2 = "DgIMvyTCMzi"
        val dir2 = File(context.filesDir, "snapshots/$sc2").apply { mkdirs() }
        val thumb2 = File(dir2, "thumb.webp").apply { createPlaceholderWebp(this, "MADURAI\nKARI DOSA", Color.rgb(180, 50, 30)) }
        val moment2_1 = File(dir2, "moment_0.webp").apply { createPlaceholderWebp(this, "Mutton Chukka\nSpicing", Color.rgb(140, 40, 20)) }
        val moment2_2 = File(dir2, "moment_1.webp").apply { createPlaceholderWebp(this, "Egg & Mutton\nLayering", Color.rgb(200, 100, 30)) }

        val reel2 = ReelEntity(
            shortcode = sc2,
            originalUrl = "https://www.instagram.com/reel/$sc2/",
            cleanUrl = "https://www.instagram.com/reel/$sc2/",
            creator = "madurai_samayal",
            sourceLanguage = "Tamil",
            title = "Authentic Madurai Kari Dosa (Spicy Mutton Crepe)",
            summary = "Direct step-by-step method to cook traditional Madurai street-style Kari Dosa layered with spiced minced mutton, whipped eggs, and fragrant shallot masala.",
            category = "Recipes & Cooking",
            tags = gson.toJson(listOf("madurai", "kari dosa", "south indian", "mutton")),
            transcript = "[0:00] First prepare the mutton chuka with pepper and sombu. [0:08] Spread thick batter on cast iron tawa. [0:14] Pour egg and layered minced mutton, flip with ghee.",
            originalTranscript = "[0:00] Mudhalla nalla milagu sombu sethu kari chuka senjidunga. [0:08] Kal la kettiyaana maavu oothitu. [0:14] Mela muttai oothi kothina kari pottu nalla nei oothi thiruppi podunga.",
            keyTakeaways = gson.toJson(listOf(
                "Cast iron skillet on medium-high heat gives the essential crisp base.",
                "Layer egg before mutton so the meat binds securely to the crepe.",
                "Use cold-pressed gingelly oil or pure ghee for authenticity."
            )),
            keyMoments = gson.toJson(listOf(
                KeyMomentWithImage(4, "Spiced minced mutton chukku consistency", moment2_1.absolutePath),
                KeyMomentWithImage(12, "Layering egg and meat over thick batter", moment2_2.absolutePath)
            )),
            structuredContent = gson.toJson(StructuredContent(
                noteType = "recipe",
                ingredients = listOf(
                    RecipeIngredient("Thick dosa batter", "1 ladle"),
                    RecipeIngredient("Minced mutton keema", "150g"),
                    RecipeIngredient("Eggs", "2 whisked"),
                    RecipeIngredient("Pure Ghee", "2 tbsp")
                ),
                steps = listOf(
                    RecipeStep(1, "Heat seasoned cast iron tawa and pour one thick ladle of batter without spreading thin."),
                    RecipeStep(2, "Immediately pour seasoned beaten egg across the wet surface."),
                    RecipeStep(3, "Press 150g cooked mutton keema on top and drizzle ghee around the perimeter.")
                )
            )),
            extractedResources = gson.toJson(listOf(
                ExtractedResource("Simmakkal Kari Dosa Masala", "", "Traditional fennel-heavy spice blend")
            )),
            caveats = gson.toJson(listOf("Oven cooking is not recommended; requires stovetop tawa")),
            useWhen = "Use when preparing a high-protein Sunday South Indian brunch.",
            thumbnailPath = thumb2.absolutePath,
            status = "REVIEWED",
            processingState = "COMPLETED"
        )

        db.reelDao().insert(reel1)
        db.reelDao().insert(reel2)
        }
    }

    private fun createPlaceholderWebp(file: File, text: String, bgColor: Int) {
        val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val lines = text.split("\n")
        var y = 220f
        lines.forEach { line ->
            canvas.drawText(line, 180f, y, paint)
            y += 36f
        }

        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.WEBP, 85, fos)
        fos.flush()
        fos.close()
        bitmap.recycle()
    }
}
