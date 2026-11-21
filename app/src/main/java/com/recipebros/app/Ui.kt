package com.recipebros.app

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Pine = Color(0xFF245B3F)
private val DeepPine = Color(0xFF173E2D)
private val Sage = Color(0xFFE9F1E8)
private val Cream = Color(0xFFF8F9F5)
private val WarmCream = Color(0xFFFFEAD6)
private val Terra = Color(0xFFAF613E)
private val Ink = Color(0xFF203126)
private val Muted = Color(0xFF6B766D)

private enum class Page { HOME, RECIPES, EDIT, DETAIL, COOK, GROCERY, MAMA, PANTRY }

private val LocalTapFeedback = staticCompositionLocalOf<() -> Unit> { {} }

private data class DockItem(val icon: ImageVector, val title: String, val action: () -> Unit)

@Composable
fun RecipeBrosApp(incomingText: String?, consumeIncoming: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings = remember { context.getSharedPreferences("recipe_bros_ui", 0) }
    val store = remember { RecipeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf(store.recipes()) }
    var groceries by remember { mutableStateOf(store.groceries()) }
    var page by remember { mutableStateOf(Page.HOME) }
    var editReturnPage by remember { mutableStateOf(Page.HOME) }
    var detailReturnPage by remember { mutableStateOf(Page.RECIPES) }
    var drawerOpen by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Recipe?>(null) }
    var draft by remember { mutableStateOf<Recipe?>(null) }
    var importMessage by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var cookStep by remember { mutableIntStateOf(0) }
    var pantryInput by rememberSaveable { mutableStateOf("") }
    var groceryDialog by remember { mutableStateOf(false) }
    var pantryDialog by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(settings.getBoolean("click_sound", true)) }
    var hapticsEnabled by remember { mutableStateOf(settings.getBoolean("click_haptics", true)) }
    var tabFraction by rememberSaveable { mutableFloatStateOf(settings.getFloat("dock_position", 0.48f).coerceIn(0f, 1f)) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var tabDragStart by remember { mutableFloatStateOf(tabFraction) }

    val tap: () -> Unit = {
        if (soundEnabled) view.playSoundEffect(SoundEffectConstants.CLICK)
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun reload() { recipes = store.recipes(); groceries = store.groceries() }
    fun openEditor(recipe: Recipe? = null) {
        editReturnPage = page
        draft = recipe ?: Recipe(title = "")
        importMessage = ""
        page = Page.EDIT
        drawerOpen = false
    }
    fun openRecipe(recipe: Recipe) {
        detailReturnPage = page
        active = recipe
        cookStep = 0
        page = Page.DETAIL
        drawerOpen = false
    }
    fun navigate(to: Page) {
        if (to == Page.COOK && page != Page.COOK) cookStep = 0
        page = to
        drawerOpen = false
    }
    fun addActiveIngredients() {
        active?.let { store.addMissingIngredients(it.ingredients); reload(); navigate(Page.GROCERY) }
    }
    fun importShared(text: String) {
        editReturnPage = Page.HOME
        draft = Recipe(title = "", sourceUrl = RecipeImporter.findUrl(text) ?: text)
        page = Page.EDIT
        drawerOpen = false
        importMessage = "Importing shared link..."
        importing = true
        scope.launch {
            try {
                val result = RecipeImporter.import(text)
                draft = result.recipe
                importMessage = result.status
            } finally { importing = false }
        }
    }

    LaunchedEffect(incomingText) {
        if (!incomingText.isNullOrBlank()) {
            importShared(incomingText)
            consumeIncoming()
        }
    }

    BackHandler(enabled = drawerOpen || page != Page.HOME) {
        when {
            drawerOpen -> drawerOpen = false
            page == Page.COOK -> page = Page.DETAIL
            page == Page.DETAIL -> page = detailReturnPage
            page == Page.EDIT -> page = editReturnPage
            else -> page = Page.HOME
        }
    }

    val screenActions = when (page) {
        Page.HOME -> listOf(
            DockItem(Icons.Rounded.Link, "Import a recipe", { openEditor() }),
            DockItem(Icons.Rounded.Bookmark, "Saved recipes", { navigate(Page.RECIPES) }),
            DockItem(Icons.Rounded.Lightbulb, "What can we cook?", { navigate(Page.PANTRY) })
        )
        Page.RECIPES -> listOf(
            DockItem(Icons.Rounded.Add, "Add new recipe", { openEditor() }),
            DockItem(Icons.Rounded.Favorite, "Family recipes", { navigate(Page.MAMA) })
        )
        Page.MAMA -> listOf(
            DockItem(Icons.Rounded.Add, "Add Mamá's recipe", { openEditor(Recipe(title = "", isMama = true)) }),
            DockItem(Icons.Rounded.MenuBook, "All saved recipes", { navigate(Page.RECIPES) })
        )
        Page.DETAIL -> listOf(
            DockItem(Icons.Rounded.Restaurant, "Start Cook Mode", { if (!active?.steps.isNullOrEmpty()) navigate(Page.COOK) }),
            DockItem(Icons.Rounded.ShoppingCart, "Shop ingredients", { addActiveIngredients() }),
            DockItem(Icons.Rounded.Edit, "Edit this recipe", { active?.let { openEditor(it) } })
        )
        Page.COOK -> listOf(
            DockItem(Icons.Rounded.SkipNext, "Next cooking step", { active?.let { cookStep = (cookStep + 1).coerceAtMost(it.steps.lastIndex.coerceAtLeast(0)) }; drawerOpen = false }),
            DockItem(Icons.Rounded.SkipPrevious, "Previous step", { cookStep = (cookStep - 1).coerceAtLeast(0); drawerOpen = false }),
            DockItem(Icons.Rounded.MenuBook, "Back to recipe", { navigate(Page.DETAIL) })
        )
        Page.GROCERY -> listOf(
            DockItem(Icons.Rounded.Add, "Add grocery item", { groceryDialog = true; drawerOpen = false }),
            DockItem(Icons.Rounded.ClearAll, "Clear checked items", { store.removeChecked(); reload(); drawerOpen = false })
        )
        Page.PANTRY -> listOf(
            DockItem(Icons.Rounded.Add, "Add pantry ingredients", { pantryDialog = true; drawerOpen = false }),
            DockItem(Icons.Rounded.MenuBook, "Find saved recipes", { navigate(Page.RECIPES) })
        )
        Page.EDIT -> listOf(
            DockItem(Icons.Rounded.Link, "Import a link below", { drawerOpen = false }),
            DockItem(Icons.Rounded.ArrowBack, "Back to previous screen", { navigate(editReturnPage) })
        )
    }
    val primaryIcon = when (page) {
        Page.DETAIL -> Icons.Rounded.ShoppingCart
        Page.COOK -> Icons.Rounded.ArrowForward
        else -> Icons.Rounded.Add
    }
    val primaryLabel = when (page) {
        Page.HOME, Page.RECIPES -> "Add recipe"
        Page.MAMA -> "Add family recipe"
        Page.GROCERY -> "Add item"
        Page.PANTRY -> "Add ingredients"
        Page.DETAIL -> "Shop ingredients"
        Page.COOK -> if (active != null && cookStep >= active!!.steps.lastIndex) "Finished" else "Next step"
        Page.EDIT -> ""
    }
    val primaryAction: () -> Unit = {
        when (page) {
            Page.HOME, Page.RECIPES -> openEditor()
            Page.MAMA -> openEditor(Recipe(title = "", isMama = true))
            Page.GROCERY -> groceryDialog = true
            Page.PANTRY -> pantryDialog = true
            Page.DETAIL -> addActiveIngredients()
            Page.COOK -> if (active != null && cookStep >= active!!.steps.lastIndex) navigate(Page.DETAIL)
                else cookStep++
            Page.EDIT -> Unit
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pine, onPrimary = Color.White, primaryContainer = Sage,
            secondary = Terra, onSecondary = Color.White,
            background = Cream, onBackground = Ink, surface = Color.White,
            onSurface = Ink, surfaceVariant = Sage, onSurfaceVariant = Muted
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        CompositionLocalProvider(LocalTapFeedback provides tap) {
            Box(Modifier.fillMaxSize().background(Cream)) {
                BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                    val trackPx = (screenHeightPx - with(LocalDensity.current) { 105.dp.toPx() }).coerceAtLeast(1f)
                    val swipeThresholdPx = with(LocalDensity.current) { 45.dp.toPx() }

                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(190)) + slideInHorizontally(tween(210)) { it / 10 })
                                .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 12 })
                        },
                        label = "Recipe Bros page"
                    ) { currentPage ->
                        when (currentPage) {
                            Page.HOME -> HomePage(
                                recipes = recipes, search = search, onSearch = { search = it },
                                onOpen = ::openRecipe, onAdd = { openEditor() },
                                onRecipes = { navigate(Page.RECIPES) },
                                onGroceries = { navigate(Page.GROCERY) },
                                onMama = { navigate(Page.MAMA) },
                                onPantry = { navigate(Page.PANTRY) }
                            )
                            Page.RECIPES, Page.MAMA -> RecipeListPage(
                                recipes = recipes, mamaOnly = currentPage == Page.MAMA,
                                onOpen = ::openRecipe,
                                onAdd = { openEditor(if (currentPage == Page.MAMA) Recipe(title = "", isMama = true) else null) },
                                onBack = { navigate(Page.HOME) }
                            )
                            Page.EDIT -> RecipeEditor(
                                initial = draft ?: Recipe(title = ""),
                                message = importMessage, importing = importing,
                                onBack = { navigate(editReturnPage) },
                                onFetch = { url ->
                                    importing = true
                                    importMessage = "Reading recipe website..."
                                    scope.launch {
                                        try {
                                            val result = RecipeImporter.import(url)
                                            draft = result.recipe
                                            importMessage = result.status
                                        } finally { importing = false }
                                    }
                                },
                                onSave = { recipe ->
                                    val id = store.save(recipe)
                                    reload()
                                    active = recipes.firstOrNull { it.id == id }
                                    navigate(Page.DETAIL)
                                }
                            )
                            Page.DETAIL -> if (active != null) RecipeDetail(
                                recipe = active!!,
                                onBack = { navigate(detailReturnPage) },
                                onEdit = { openEditor(active) },
                                onCook = { navigate(Page.COOK) },
                                onList = { addActiveIngredients() },
                                onFavorite = {
                                    store.save(active!!.copy(favorite = !active!!.favorite))
                                    reload()
                                    active = recipes.firstOrNull { it.id == active!!.id }
                                },
                                onDelete = {
                                    store.deleteRecipe(active!!.id)
                                    active = null
                                    reload()
                                    navigate(detailReturnPage)
                                }
                            ) else EmptyDetail(onBack = { navigate(Page.HOME) })
                            Page.COOK -> if (active != null) CookPage(
                                active!!, stepIndex = cookStep, onStepIndex = { cookStep = it },
                                onBack = { navigate(Page.DETAIL) }
                            ) else EmptyDetail(onBack = { navigate(Page.RECIPES) })
                            Page.GROCERY -> GroceryPage(
                                groceries, onAdd = { store.addGrocery(it); reload() },
                                onChecked = { id, checked -> store.setGroceryChecked(id, checked); reload() },
                                onDelete = { store.deleteGrocery(it); reload() },
                                onClearChecked = { store.removeChecked(); reload() }
                            )
                            Page.PANTRY -> PantryPage(
                                recipes = recipes, onOpen = ::openRecipe,
                                pantry = pantryInput, onPantryChange = { pantryInput = it }
                            )
                        }
                    }

                    if (page != Page.EDIT && !drawerOpen) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                                Text(primaryLabel, Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    color = Pine, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (pressed) 0.89f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "FAB spring")
                            Surface(
                                modifier = Modifier.size(57.dp).scale(scale)
                                    .clickable(interactionSource = interaction,
                                        indication = androidx.compose.foundation.LocalIndication.current) {
                                        tap(); primaryAction()
                                    },
                                color = Pine, shape = CircleShape, shadowElevation = 7.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(primaryIcon, primaryLabel, tint = Color.White,
                                        modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    }

                    if (!drawerOpen) {
                        Surface(
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .offset { IntOffset(0, ((tabFraction - 0.5f) * trackPx).roundToInt()) }
                                .width(30.dp).height(76.dp)
                                .pointerInput(trackPx, swipeThresholdPx) {
                                    detectDragGestures(
                                        onDragStart = { horizontalDrag = 0f; tabDragStart = tabFraction },
                                        onDragEnd = {
                                            if (horizontalDrag < -swipeThresholdPx) {
                                                tap()
                                                drawerOpen = true
                                            } else if (kotlin.math.abs(tabFraction - tabDragStart) > 0.005f) {
                                                tap() // gentle confirmation when the handle is repositioned
                                            }
                                            horizontalDrag = 0f
                                        },
                                        onDragCancel = { horizontalDrag = 0f }
                                    ) { change, distance ->
                                        change.consume()
                                        horizontalDrag += distance.x
                                        if (kotlin.math.abs(distance.y) >= kotlin.math.abs(distance.x)) {
                                            tabFraction = (tabFraction + distance.y / trackPx).coerceIn(0f, 1f)
                                            settings.edit().putFloat("dock_position", tabFraction).apply()
                                        }
                                    }
                                }
                                .clickable { tap(); drawerOpen = true },
                            color = Pine,
                            shape = RoundedCornerShape(topStart = 19.dp, bottomStart = 19.dp),
                            shadowElevation = 7.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ChevronLeft, "Open quick tools — drag up or down to move",
                                    tint = Color.White, modifier = Modifier.size(23.dp))
                            }
                        }
                    }

                    if (drawerOpen) {
                        Box(Modifier.fillMaxSize().background(Color(0x77000000)).clickable { drawerOpen = false })
                    }
                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = slideInHorizontally(tween(235)) { it } + fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(180)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight(0.90f).fillMaxWidth(0.82f),
                            color = DeepPine,
                            shape = RoundedCornerShape(topStart = 27.dp, bottomStart = 27.dp),
                            shadowElevation = 18.dp
                        ) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Restaurant, null, tint = Color(0xFFE6F5D1),
                                        modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Recipe Bros", color = Color.White, fontSize = 20.sp,
                                        fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { tap(); drawerOpen = false }) {
                                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                                    }
                                }
                                Text("YOUR SCREEN · QUICK ACTIONS", color = Color(0xFFFFD6B1),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(6.dp))
                                screenActions.forEach { item ->
                                    DrawerAction(item.icon, item.title, item.action)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFF47745B))
                                Text("GO ANYWHERE", color = Color(0xFFB8D5BB), fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                DrawerAction(Icons.Rounded.Home, "Home") { navigate(Page.HOME) }
                                DrawerAction(Icons.Rounded.Bookmark, "My saved recipes") { navigate(Page.RECIPES) }
                                DrawerAction(Icons.Rounded.ShoppingCart, "Grocery list") { navigate(Page.GROCERY) }
                                DrawerAction(Icons.Rounded.Favorite, "Las Recetas de Mamá ❤️") { navigate(Page.MAMA) }
                                DrawerAction(Icons.Rounded.Lightbulb, "What can we cook?") { navigate(Page.PANTRY) }
                                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFF47745B))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Click sounds", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Switch(checked = soundEnabled, onCheckedChange = {
                                        soundEnabled = it
                                        settings.edit().putBoolean("click_sound", it).apply()
                                    })
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Haptic taps", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Switch(checked = hapticsEnabled, onCheckedChange = {
                                        hapticsEnabled = it
                                        settings.edit().putBoolean("click_haptics", it).apply()
                                    })
                                }
                                Text("Monterrey  •  Zacatecas", color = Color(0xFFFFDAB4), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (groceryDialog) {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { groceryDialog = false },
                    title = { Text("Add grocery item") },
                    text = { OutlinedTextField(text, { text = it }, singleLine = true,
                        placeholder = { Text("Milk, tortillas, chiles...") }) },
                    confirmButton = { TextButton(onClick = {
                        if (text.isNotBlank()) { store.addGrocery(text.trim()); reload(); tap() }
                        groceryDialog = false
                    }, enabled = text.isNotBlank()) { Text("Add") } },
                    dismissButton = { TextButton(onClick = { groceryDialog = false }) { Text("Cancel") } }
                )
            }
            if (pantryDialog) {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { pantryDialog = false },
                    title = { Text("What's in the kitchen?") },
                    text = { OutlinedTextField(text, { text = it }, minLines = 2,
                        placeholder = { Text("Rice, chicken, tomato...") }) },
                    confirmButton = { TextButton(onClick = {
                        if (text.isNotBlank()) {
                            pantryInput = listOf(pantryInput, text.trim()).filter { it.isNotBlank() }.joinToString(", ")
                            tap()
                        }
                        pantryDialog = false
                    }, enabled = text.isNotBlank()) { Text("Add ingredients") } },
                    dismissButton = { TextButton(onClick = { pantryDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, action: () -> Unit) {
    val tap = LocalTapFeedback.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
        .clickable { tap(); action() }
        .padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFE5EFCF), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BrandHeader(subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val tap = LocalTapFeedback.current
    Row(
        Modifier.fillMaxWidth().height(57.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = { tap(); onBack() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = Pine, modifier = Modifier.size(23.dp))
            }
        } else {
            Surface(shape = RoundedCornerShape(12.dp), color = DeepPine,
                modifier = Modifier.size(37.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Restaurant, "Recipe Bros logo", tint = Color.White,
                        modifier = Modifier.size(23.dp))
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recipe", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DeepPine)
                Spacer(Modifier.width(4.dp))
                Text("Bros", fontSize = 22.sp, fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic, color = Color(0xFF489B4D))
            }
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 10.sp,
                maxLines = 1, color = Muted, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun HomePage(recipes: List<Recipe>, search: String, onSearch: (String) -> Unit,
                     onOpen: (Recipe) -> Unit, onAdd: () -> Unit, onRecipes: () -> Unit,
                     onGroceries: () -> Unit, onMama: () -> Unit, onPantry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)) {
        BrandHeader("SAVE  •  COOK  •  LEARN  •  SHARE")
        Spacer(Modifier.height(3.dp))
        Text("¿Qué cocinamos hoy?", fontWeight = FontWeight.Bold, fontSize = 21.sp,
            lineHeight = 24.sp, color = Ink, maxLines = 1)
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp),
            placeholder = { Text("Search your recipes...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
            singleLine = true, shape = RoundedCornerShape(19.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile(Icons.Rounded.Bookmark, "Saved", onRecipes, Modifier.weight(1f))
            QuickTile(Icons.Rounded.Link, "Import", onAdd, Modifier.weight(1f))
            QuickTile(Icons.Rounded.ShoppingCart, "Groceries", onGroceries, Modifier.weight(1f))
        }
        Spacer(Modifier.height(13.dp))
        MamaFeature(onClick = onMama)
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Recipes", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onRecipes, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("View all  ›", fontSize = 12.sp)
            }
        }
        val filtered = if (search.isBlank()) recipes else recipes.filter {
            it.title.contains(search, true) ||
                it.ingredients.any { ingredient -> ingredient.contains(search, true) } ||
                it.region.contains(search, true)
        }
        if (filtered.isEmpty()) {
            EmptyCard(if (search.isBlank()) "Start your cookbook! Tap + or share a recipe link."
                else "No saved recipes match that search.", onAdd)
        } else {
            filtered.take(4).forEach { recipe ->
                RecipeCard(recipe) { onOpen(recipe) }
                Spacer(Modifier.height(7.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val tap = LocalTapFeedback.current
        Surface(color = Sage, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable { tap(); onPantry() }) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Pine, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("What Can We Cook?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Use ingredients you already have", fontSize = 11.sp, color = Muted)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Pine)
            }
        }
        Spacer(Modifier.height(94.dp))
    }
}

@Composable
private fun QuickTile(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tap = LocalTapFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shrink by animateFloatAsState(if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "Tile tap")
    Surface(
        modifier = modifier.height(73.dp).scale(shrink)
            .clickable(interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current) {
                tap(); onClick()
            },
        color = Sage, shape = RoundedCornerShape(17.dp)
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Pine, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(5.dp))
            Text(text, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun MamaFeature(onClick: () -> Unit) {
    val tap = LocalTapFeedback.current
    Surface(shape = RoundedCornerShape(21.dp),
        modifier = Modifier.fillMaxWidth().clickable { tap(); onClick() }) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(Color(0xFF8E5039),
                Terra, Color(0xFFCB9366)))).height(128.dp)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("♥  RECETAS DE LA FAMILIA", color = Color(0xFFFFE1C4),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                Text("Las Recetas de Mamá", color = Color.White,
                    fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(5.dp))
                Text("MONTERREY  •  ZACATECAS", color = Color(0xFFFFE8CE),
                    fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Text("Open family cookbook  →", color = Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = Color(0x35FFFFFF), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, "Mama's recipes", tint = Color.White,
                        modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.MenuBook, null, tint = Pine, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onAdd) { Text("+ Add your first recipe") }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val tap = LocalTapFeedback.current
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp),
        modifier = Modifier.fillMaxWidth().clickable { tap(); onClick() }, shadowElevation = 1.dp) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = if (recipe.isMama) WarmCream else Sage,
                modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (recipe.isMama) Icons.Rounded.Favorite else Icons.Rounded.Restaurant,
                        null, tint = if (recipe.isMama) Terra else Pine, modifier = Modifier.size(27.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(recipe.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                val subtitle = when {
                    recipe.isMama -> "❤️ Las Recetas de Mamá${if (recipe.region.isNotBlank()) " • ${recipe.region}" else ""}"
                    recipe.sourceLabel.isNotBlank() -> recipe.sourceLabel
                    else -> "Personal cookbook"
                }
                Text(subtitle, fontSize = 12.sp, color = Muted, maxLines = 1)
                val duration = recipe.prepMinutes + recipe.cookMinutes
                if (duration > 0) Text("⏱ $duration min  •  ${recipe.servings} servings", fontSize = 12.sp, color = Pine)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Pine)
        }
    }
}

@Composable
private fun RecipeListPage(recipes: List<Recipe>, mamaOnly: Boolean, onOpen: (Recipe) -> Unit,
                           onAdd: () -> Unit, onBack: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val listed = recipes.filter { (!mamaOnly || it.isMama) &&
        (search.isBlank() || it.title.contains(search, true) || it.ingredients.any { x -> x.contains(search, true) }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        BrandHeader(if (mamaOnly) "Monterrey • Zacatecas • Familia" else "Your personal cookbook", onBack)
        Text(if (mamaOnly) "Las Recetas de Mamá ❤️" else "Saved recipes",
            fontSize = 25.sp, fontWeight = FontWeight.Bold, color = if (mamaOnly) Terra else Ink)
        if (mamaOnly) Text("A space for real family recipes, exactly as Mamá makes them.", color = Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text("Search cookbook") })
        Spacer(Modifier.height(10.dp))
        Text("Tap the floating + to add a recipe", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        if (listed.isEmpty()) {
            EmptyCard(if (mamaOnly) "No family recipes yet. Add a real recipe from Mamá to preserve it here."
                else "No recipes yet. Share a link from your browser, Facebook, or another app.", onAdd)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
                items(listed, key = { it.id }) { recipe -> RecipeCard(recipe) { onOpen(recipe) } }
            }
        }
    }
}

@Composable
private fun RecipeEditor(initial: Recipe, message: String, importing: Boolean, onBack: () -> Unit,
                         onFetch: (String) -> Unit, onSave: (Recipe) -> Unit) {
    val tap = LocalTapFeedback.current
    var title by remember(initial) { mutableStateOf(initial.title) }
    var url by remember(initial) { mutableStateOf(initial.sourceUrl) }
    var ingredients by remember(initial) { mutableStateOf(initial.ingredients.joinToString("\n")) }
    var steps by remember(initial) { mutableStateOf(initial.steps.joinToString("\n")) }
    var prep by remember(initial) { mutableStateOf(initial.prepMinutes.toString()) }
    var cook by remember(initial) { mutableStateOf(initial.cookMinutes.toString()) }
    var servings by remember(initial) { mutableStateOf(initial.servings.toString()) }
    var mama by remember(initial) { mutableStateOf(initial.isMama) }
    var region by remember(initial) { mutableStateOf(initial.region) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    var showValidation by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        BrandHeader("Import from a link or write your own", onBack)
        Text(if (initial.id == 0L) "Save a recipe" else "Edit recipe",
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Original recipe link (optional)") },
            placeholder = { Text("Paste a website or video link") }, maxLines = 3)
        Spacer(Modifier.height(7.dp))
        OutlinedButton(onClick = { tap(); onFetch(url) }, enabled = !importing && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(8.dp))
            Text(if (importing) "Reading link..." else "Try extracting recipe from link")
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(message, color = Terra, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Recipe name *") },
            singleLine = true, isError = showValidation && title.isBlank())
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(ingredients, { ingredients = it }, Modifier.fillMaxWidth().heightIn(min = 125.dp),
            label = { Text("Ingredients — one per line") }, placeholder = { Text("2 cups rice\n1 tomato\n1 onion") }, minLines = 4)
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(steps, { steps = it }, Modifier.fillMaxWidth().heightIn(min = 170.dp),
            label = { Text("Instructions — one step per line") }, placeholder = { Text("Wash the rice\nHeat oil\nAdd ingredients") }, minLines = 5)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberField(prep, { prep = it }, "Prep min", Modifier.weight(1f))
            NumberField(cook, { cook = it }, "Cook min", Modifier.weight(1f))
            NumberField(servings, { servings = it }, "Serves", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = WarmCream, shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mama, onCheckedChange = { mama = it })
                    Text("❤️ Add to Las Recetas de Mamá", fontWeight = FontWeight.SemiBold, color = Terra)
                }
                if (mama) {
                    OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(),
                        label = { Text("Region / family (Monterrey, Zacatecas...)") })
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Family tips / personal notes") }, minLines = 2)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            tap()
            if (title.isBlank()) { showValidation = true; return@Button }
            onSave(initial.copy(
                title = title.trim(), sourceUrl = url.trim(),
                sourceLabel = runCatching { Uri.parse(url.trim()).host?.removePrefix("www.") ?: "" }.getOrDefault(""),
                ingredients = ingredients.lines().map(String::trim).filter(String::isNotBlank),
                steps = steps.lines().map(String::trim).filter(String::isNotBlank),
                prepMinutes = (prep.toIntOrNull() ?: 0).coerceIn(0, 1440),
                cookMinutes = (cook.toIntOrNull() ?: 0).coerceIn(0, 1440),
                servings = (servings.toIntOrNull() ?: 2).coerceIn(1, 100),
                isMama = mama, region = region.trim(), notes = notes.trim()
            ))
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Save to My Cookbook")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(value, onValueChange = { onValue(it.filter(Char::isDigit).take(4)) },
        modifier = modifier, singleLine = true, label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun RecipeDetail(recipe: Recipe, onBack: () -> Unit, onEdit: () -> Unit,
                         onCook: () -> Unit, onList: () -> Unit,
                         onFavorite: () -> Unit, onDelete: () -> Unit) {
    val tap = LocalTapFeedback.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this recipe?") },
        text = { Text("This permanently removes your saved recipe and notes from this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Terra) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        BrandHeader("Your saved recipe", onBack)
        if (recipe.isMama) {
            Text("❤️ LAS RECETAS DE MAMÁ", color = Terra, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(recipe.title, fontSize = 29.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
        if (recipe.region.isNotBlank()) Text("📍 ${recipe.region}", color = Terra)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Prep ${recipe.prepMinutes}m")
            InfoPill("Cook ${recipe.cookMinutes}m")
            InfoPill("${recipe.servings} servings")
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { tap(); onFavorite() }) {
                Icon(if (recipe.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Toggle favorite", tint = Terra)
            }
            FilledTonalIconButton(onClick = { tap(); onEdit() }) { Icon(Icons.Rounded.Edit, "Edit recipe") }
            FilledTonalIconButton(onClick = { tap(); confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete recipe") }
            if (recipe.sourceUrl.startsWith("https://") || recipe.sourceUrl.startsWith("http://")) {
                FilledTonalIconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.sourceUrl))) }
                }) { Icon(Icons.Rounded.OpenInNew, "Open original link") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { tap(); onCook() }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = recipe.steps.isNotEmpty()) {
            Icon(Icons.Rounded.Restaurant, null); Spacer(Modifier.width(8.dp)); Text("Start Cook Mode")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { tap(); onList() }, modifier = Modifier.fillMaxWidth(), enabled = recipe.ingredients.isNotEmpty()) {
            Icon(Icons.Rounded.ShoppingCart, null); Spacer(Modifier.width(8.dp)); Text("Add ingredients to grocery list")
        }
        if (recipe.ingredients.isEmpty() || recipe.steps.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("This saved link is missing some recipe details. Tap the pencil to fill them in.", color = Terra, fontSize = 12.sp)
        }
        Spacer(Modifier.height(21.dp))
        Text("Ingredients", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                if (recipe.ingredients.isEmpty()) Text("No ingredients saved yet", color = Muted)
                recipe.ingredients.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Pine, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(item, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Step-by-step", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        recipe.steps.forEachIndexed { index, instruction ->
            Surface(color = Color.White, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = Sage, shape = CircleShape) {
                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = Pine, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(instruction, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        if (recipe.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Family tips & notes", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Terra)
            Text(recipe.notes, modifier = Modifier.padding(top = 7.dp), color = Ink)
        }
        if (recipe.sourceUrl.isNotBlank()) {
            Spacer(Modifier.height(17.dp))
            Text("Original link: ${recipe.sourceUrl}", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(94.dp))
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(color = Sage, shape = CircleShape) {
        Text(text, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            color = Pine, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun cookingTip(instruction: String): String {
    val line = instruction.lowercase()
    return when {
        "sauté" in line || "saute" in line || "sofreír" in line || "sofreir" in line ->
            "Sauté / sofreír means cook in a little oil over medium heat while stirring. Watch for softening and aroma; don't let it burn."
        "simmer" in line || "hervir a fuego lento" in line ->
            "Simmer means gentle bubbles, not a hard boil. Reduce heat until the surface moves lightly."
        "boil" in line || "hervir" in line ->
            "Boil means the liquid is bubbling vigorously. Lower the heat after boiling if the recipe asks for a simmer."
        "dice" in line || "picar" in line ->
            "Dice means cut the ingredient into small, fairly even cubes. Keep fingers curled away from the knife blade."
        "mince" in line || "finamente" in line ->
            "Mince means chop very finely, usually for garlic, onion, or herbs."
        "sear" in line || "sellar" in line ->
            "Sear means cook the outside over fairly high heat for browning. Use ventilation and avoid splashing hot oil."
        "toast" in line || "tostar" in line ->
            "Toast means heat until fragrant or lightly browned. Stir or turn frequently to avoid burning."
        "bake" in line || "hornea" in line ->
            "Preheat the oven first. Use oven mitts and follow the recipe's time and safe internal-temperature guidance."
        "fold" in line || "incorporar" in line ->
            "Fold means gently lift and turn the mixture using a spatula so you don't knock too much air out."
        "medium" in line || "fuego medio" in line ->
            "Medium heat is the middle range on most stoves. Adjust if food is browning too quickly."
        else -> "Read the whole step before starting. Measure ingredients first, work carefully, and check that foods like meat and poultry reach a safe internal temperature. This is a general tip; the original recipe is your source for exact times."
    }
}

@Composable
private fun CookPage(recipe: Recipe, stepIndex: Int, onStepIndex: (Int) -> Unit,
                     onBack: () -> Unit) {
    val index = stepIndex
    val tap = LocalTapFeedback.current
    var showTip by remember(recipe.id) { mutableStateOf(false) }
    LaunchedEffect(recipe.id, index) { showTip = false }
    var timerMinutes by remember(recipe.id) { mutableStateOf("5") }
    var remaining by remember(recipe.id) { mutableIntStateOf(300) }
    var running by remember(recipe.id) { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining = (remaining - 1).coerceAtLeast(0)
        }
        if (remaining == 0) running = false
    }
    val step = recipe.steps.getOrNull(index)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        BrandHeader("Distraction-free, step-by-step cooking", onBack)
        Text("Cook Mode", color = Pine, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(recipe.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
        Spacer(Modifier.height(13.dp))
        if (step != null) {
            Text("STEP ${index + 1} OF ${recipe.steps.size}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (index + 1f) / recipe.steps.size }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(13.dp))
            AnimatedContent(targetState = index, label = "Cooking step transition",
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 5 })
                        .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { -it / 5 })
                }) { shownIndex ->
                Surface(color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(19.dp)) {
                        Text("Step ${shownIndex + 1}", color = Pine, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        Text(recipe.steps[shownIndex], fontSize = 20.sp, lineHeight = 28.sp, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = Sage, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().clickable { tap(); showTip = !showTip }) {
                Column(Modifier.animateContentSize().padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = Pine)
                        Spacer(Modifier.width(9.dp))
                        Text("Beginner Cooking Assistant", fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), color = Pine)
                        Icon(if (showTip) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Pine)
                    }
                    if (showTip) {
                        Spacer(Modifier.height(12.dp))
                        Text(cookingTip(step), fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Built-in beginner guidance • Not live AI analysis", fontSize = 10.sp, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = Pine)
                        Spacer(Modifier.width(8.dp))
                        Text("Kitchen Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("%02d:%02d".format(remaining / 60, remaining % 60),
                        fontSize = 46.sp, color = Pine, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(timerMinutes, { timerMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.width(100.dp), label = { Text("Minutes") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        FilledTonalButton(onClick = {
                            tap()
                            running = false
                            remaining = (timerMinutes.toIntOrNull() ?: 5).coerceIn(1, 999) * 60
                        }) { Text("Set") }
                        Button(onClick = { tap(); if (remaining > 0) running = !running }) {
                            Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        }
                    }
                    if (remaining == 0) Text("Time's up! Check your food.", color = Terra)
                }
            }
            Spacer(Modifier.height(21.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { tap(); onStepIndex(index - 1) }, enabled = index > 0,
                    modifier = Modifier.weight(1f)) { Text("← Previous") }
                Button(onClick = { tap(); onStepIndex(index + 1) }, enabled = index < recipe.steps.lastIndex,
                    modifier = Modifier.weight(1f)) { Text("Next →") }
            }
            if (index == recipe.steps.lastIndex) {
                Spacer(Modifier.height(14.dp))
                Text("Last step! You're almost there. Buen provecho! ❤️",
                    color = Terra, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text("This recipe has no steps yet. Go back and edit it.", color = Muted)
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun GroceryPage(groceries: List<GroceryItem>, onAdd: (String) -> Unit,
                        onChecked: (Long, Boolean) -> Unit, onDelete: (Long) -> Unit, onClearChecked: () -> Unit) {
    val tap = LocalTapFeedback.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        BrandHeader("All your groceries, any store")
        Text("Grocery List", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Any store • Tap + to add an item", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${groceries.count { !it.checked }} items left", fontSize = 13.sp, color = Muted,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { tap(); onClearChecked() }, enabled = groceries.any { it.checked }) { Text("Clear checked") }
        }
        if (groceries.isEmpty()) {
            Surface(color = Sage, shape = RoundedCornerShape(18.dp)) {
                Text("Your grocery list is empty. Tap + to add an item, or open a recipe and send ingredients here.",
                    modifier = Modifier.fillMaxWidth().padding(22.dp), color = Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
                items(groceries, key = { it.id }) { item ->
                    Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(item.checked, onCheckedChange = { tap(); onChecked(item.id, it) })
                            Text(item.text, Modifier.weight(1f),
                                color = if (item.checked) Muted else Ink, fontSize = 15.sp)
                            IconButton(onClick = { tap(); onDelete(item.id) }) {
                                Icon(Icons.Rounded.Close, "Remove item", tint = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PantryPage(recipes: List<Recipe>, onOpen: (Recipe) -> Unit,
                       pantry: String, onPantryChange: (String) -> Unit) {
    val terms = pantry.split(',', '\n').map { it.trim().lowercase() }.filter { it.length >= 2 }
    val matches = if (terms.isEmpty()) emptyList() else recipes.map { recipe ->
        val count = recipe.ingredients.count { ingredient -> terms.any { term -> term in ingredient.lowercase() } }
        Pair(recipe, count)
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        BrandHeader("Ideas from your saved cookbook")
        Text("What Can We Cook?", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Enter what's in your fridge. We'll look for matches among recipes you've saved.", color = Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pantry, onPantryChange, Modifier.fillMaxWidth(), minLines = 2,
            label = { Text("Ingredients you have") }, placeholder = { Text("Chicken, rice, tomato, onion...") })
        Spacer(Modifier.height(15.dp))
        Text("Matching saved recipes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        if (terms.isEmpty()) Text("Enter ingredients above to see suggestions.", color = Muted)
        else if (matches.isEmpty()) Text("No match in your saved cookbook yet. Save more recipes to get better matches.", color = Muted)
        matches.forEach { (recipe, count) ->
            RecipeCard(recipe, onClick = { onOpen(recipe) })
            Text("$count matching ingredient(s) • Check what else you need", color = Pine, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp, start = 12.dp, top = 3.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun EmptyDetail(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BrandHeader("Choose a saved recipe", onBack)
        Text("Open a recipe from your cookbook to see its details or start cooking.", color = Muted)
    }
}
