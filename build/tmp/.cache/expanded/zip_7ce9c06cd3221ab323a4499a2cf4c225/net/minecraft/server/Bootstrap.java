package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import org.slf4j.Logger;

public class Bootstrap {
   public static final PrintStream STDOUT = System.out;
   /** Whether the blocks, items, etc have already been registered */
   private static volatile boolean isBootstrapped;
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final AtomicLong bootstrapDuration = new AtomicLong(-1L);

   /**
    * Registers blocks, items, stats, etc.
    */
   public static void bootStrap() {
      if (!isBootstrapped) {
         isBootstrapped = true;
         Instant instant = Instant.now();
         if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
            throw new IllegalStateException("Unable to load registries");
         } else {
            FireBlock.bootStrap();
            ComposterBlock.bootStrap();
            if (EntityType.getKey(EntityType.PLAYER) == null) {
               throw new IllegalStateException("Failed loading EntityTypes");
            } else {
               PotionBrewing.bootStrap();
               EntitySelectorOptions.bootStrap();
               DispenseItemBehavior.bootStrap();
               CauldronInteraction.bootStrap();
               BuiltInRegistries.bootStrap();
               CreativeModeTabs.validate();
               net.minecraftforge.registries.GameData.vanillaSnapshot();
               // Forge: Hacky fix to ensure that NetworkConstants is loaded before mods are constructed.
               // Many older mods use network internals that shouldn't be used, yet are exposed so they get used anyways.
               // This can cause class-loading issues with ForgeMod loading NetworkConstants and HandshakeResolver.
               // To ensure that doesn't happen, we load it here and now. This is not an issue in 1.20.2 and newer.
               net.minecraftforge.network.NetworkHooks.init();
               if (false) // skip redirectOutputToLog, Forge already redirects stdout and stderr output to log so that they print with more context
               wrapStreams();
               bootstrapDuration.set(Duration.between(instant, Instant.now()).toMillis());
            }
         }
      }
   }

   private static <T> void checkTranslations(Iterable<T> pObjects, Function<T, String> pObjectToKeyFunction, Set<String> pTranslationSet) {
      Language language = Language.getInstance();
      pObjects.forEach((p_135883_) -> {
         String s = pObjectToKeyFunction.apply(p_135883_);
         if (!language.has(s)) {
            pTranslationSet.add(s);
         }

      });
   }

   private static void checkGameruleTranslations(final Set<String> pTranslations) {
      final Language language = Language.getInstance();
      GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
         public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> p_135897_, GameRules.Type<T> p_135898_) {
            if (!language.has(p_135897_.getDescriptionId())) {
               pTranslations.add(p_135897_.getId());
            }

         }
      });
   }

   public static Set<String> getMissingTranslations() {
      Set<String> set = new TreeSet<>();
      checkTranslations(BuiltInRegistries.ATTRIBUTE, Attribute::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.ENTITY_TYPE, EntityType::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.MOB_EFFECT, MobEffect::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.ITEM, Item::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.ENCHANTMENT, Enchantment::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.BLOCK, Block::getDescriptionId, set);
      checkTranslations(BuiltInRegistries.CUSTOM_STAT, (p_135885_) -> {
         return "stat." + p_135885_.toString().replace(':', '.');
      }, set);
      checkGameruleTranslations(set);
      return set;
   }

   public static void checkBootstrapCalled(Supplier<String> pCallSite) {
      if (!isBootstrapped) {
         throw createBootstrapException(pCallSite);
      }
   }

   private static RuntimeException createBootstrapException(Supplier<String> pCallSite) {
      try {
         String s = pCallSite.get();
         return new IllegalArgumentException("Not bootstrapped (called from " + s + ")");
      } catch (Exception exception) {
         RuntimeException runtimeexception = new IllegalArgumentException("Not bootstrapped (failed to resolve location)");
         runtimeexception.addSuppressed(exception);
         return runtimeexception;
      }
   }

   public static void validate() {
      checkBootstrapCalled(() -> {
         return "validate";
      });
      if (SharedConstants.IS_RUNNING_IN_IDE) {
         getMissingTranslations().forEach((p_179915_) -> {
            LOGGER.error("Missing translations: {}", (Object)p_179915_);
         });
         Commands.validate();
      }

   }

   /**
    * Redirect standard streams to logger.
    */
   private static void wrapStreams() {
      if (LOGGER.isDebugEnabled()) {
         System.setErr(new DebugLoggedPrintStream("STDERR", System.err));
         System.setOut(new DebugLoggedPrintStream("STDOUT", STDOUT));
      } else {
         System.setErr(new LoggedPrintStream("STDERR", System.err));
         System.setOut(new LoggedPrintStream("STDOUT", STDOUT));
      }

   }

   public static void realStdoutPrintln(String pMessage) {
      STDOUT.println(pMessage);
   }
}
