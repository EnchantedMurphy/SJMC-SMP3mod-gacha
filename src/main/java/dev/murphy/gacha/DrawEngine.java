package dev.murphy.gacha;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.random.RandomGenerator;
import static dev.murphy.gacha.GachaConfig.*;

final class DrawEngine {
    record Result(Tier tier, Prize prize, List<Prize> fixedRewards, boolean hardPity, boolean tenGuarantee) {}
    record Batch(List<Result> results, int missesAfter) {}

    static Batch roll(PoolRules config, int misses, Map<String, Long> obtained, int draws, RandomGenerator random) {
        if (draws != 1 && draws != 10) throw new IllegalArgumentException("抽数应为 1 或 10。");
        List<Result> results = new ArrayList<>(draws);
        Map<String, Long> counts = new HashMap<>(obtained);
        for (int i = 0; i < draws; i++) {
            boolean hardPity = misses >= config.pity - 1;
            boolean tenGuarantee = draws == 10 && i == 9;
            double roll = hardPity ? 0 : random.nextDouble();
            Tier tier = hardPity || roll < config.sProbability ? Tier.S
                    : tenGuarantee || roll < config.sProbability + config.aProbability ? Tier.A : Tier.B;
            List<Prize> choices = config.rewards.get(tier).stream().filter(p -> p.limit == -1
                    || counts.getOrDefault(prizeKey(tier, p.id), 0L) < p.limit).toList();
            Prize selected = null;
            if (!choices.isEmpty()) {
                int choice = random.nextInt(choices.stream().mapToInt(p -> p.weight).sum());
                for (Prize p : choices) {
                    choice -= p.weight;
                    if (choice < 0) { selected = p; break; }
                }
                counts.merge(prizeKey(tier, selected.id), 1L, Math::addExact);
            }
            results.add(new Result(tier, selected, List.copyOf(config.fixedRewards.get(tier)), hardPity, tenGuarantee && tier == Tier.A));
            misses = tier == Tier.S ? 0 : misses + 1;
        }
        return new Batch(List.copyOf(results), misses);
    }
}
