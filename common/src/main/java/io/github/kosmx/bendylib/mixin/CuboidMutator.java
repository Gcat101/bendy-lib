package io.github.kosmx.bendylib.mixin;


import io.github.kosmx.bendylib.ICuboidBuilder;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.accessors.CuboidSideAccessor;
import io.github.kosmx.bendylib.impl.ICuboid;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"rawtypes", "unused"})
@Mixin(ModelPart.Cuboid.class)
public class CuboidMutator implements MutableCuboid, CuboidSideAccessor {

    @Shadow @Final public float minX;
    @Shadow @Final public float minY;
    @Shadow @Final public float minZ;
    @Mutable
    @Shadow @Final private ModelPart.Quad[] sides;
    //Store the mutators and the mutator builders.

    @Unique
    private HashMap<String, ICuboid> mutators = new HashMap<>();

    @Unique
    private HashMap<String, ICuboidBuilder> mutatorBuilders = new HashMap<>();

    @Unique
    private ModelPart.Quad[] originalQuads;

    @Unique
    private boolean isSidesSwapped = false;

    @Unique
    private ICuboidBuilder.Data partData;

    @Nullable
    @Unique
    private ICuboid activeMutator;

    @Nullable
    @Unique
    private String activeMutatorID;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void constructor(int u, int v, float x, float y, float z, float sizeX, float sizeY, float sizeZ, float extraX, float extraY, float extraZ, boolean mirror, float textureWidth, float textureHeight, Set set, CallbackInfo ci){
        partData = new ICuboidBuilder.Data(u, v, minX, minY, minZ, sizeX, sizeY, sizeZ, extraX, extraY, extraZ, mirror, textureWidth, textureHeight);
        originalQuads = this.sides;
    }


    @Override
    public boolean registerMutator(String name, ICuboidBuilder<ICuboid> builder) {
        if(mutatorBuilders.containsKey(name)) return false;
        if(builder == null) throw new NullPointerException("builder can not be null");
        mutatorBuilders.put(name, builder);
        return true;
    }

    @Override
    public boolean unregisterMutator(String name) {
        if(mutatorBuilders.remove(name) != null){
            if(name.equals(activeMutatorID)){
                activeMutator = null;
                activeMutatorID = null;
            }
            mutators.remove(name);

            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public Pair<String, ICuboid> getActiveMutator() {
        return activeMutator == null ? null : new Pair<>(activeMutatorID, activeMutator);
    }

    @Override
    public boolean hasMutator(String key) {
        return mutators.containsKey(key) || mutatorBuilders.containsKey(key);
    }

    @Nullable
    @Override
    public ICuboidBuilder<ICuboid> getCuboidBuilder(String key) {
        return mutatorBuilders.get(key);
    }

    @Nullable
    @Override
    public ICuboid getMutator(String name) {
        return mutators.get(name);
    }

    @Nullable
    @Override
    public ICuboid getAndActivateMutator(@Nullable String name) {
        if(name == null){
            activeMutatorID = null;
            activeMutator = null;
            return null;
        }
        if(mutatorBuilders.containsKey(name)){
            if(!mutators.containsKey(name)){
                mutators.put(name, mutatorBuilders.get(name).build(partData));
            }
            activeMutatorID = name;
            return activeMutator = mutators.get(name);
        }
        return null;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void copyStateFrom(MutableCuboid other) {
        if(other.getActiveMutator() == null){
            activeMutator = null;
            activeMutatorID = null;
        }
        else {
            if(this.getAndActivateMutator(other.getActiveMutator().getLeft()) != null){
                activeMutator.copyState(other.getActiveMutator().getRight());
            }
        }
    }

    @Inject(method = "renderCuboid", at = @At(value = "HEAD"), cancellable = true)
    private void renderRedirect(MatrixStack.Entry entry, VertexConsumer vertexConsumer, int light, int overlay, int color, CallbackInfo ci){
        if(getActiveMutator() != null){
            getActiveMutator().getRight().render(entry, vertexConsumer, light, overlay, color);
            if(getActiveMutator().getRight().disableAfterDraw()) {
                activeMutator = null; //mutator lives only for one render cycle
                activeMutatorID = null;
            }
            ci.cancel();
        }
    }

    @Override
    public void doSideSwapping(){
        if(this.getActiveMutator() != null){
            List<ModelPart.Quad> sides = this.getActiveMutator().getRight().getQuads();
            if(sides != null){
                this.isSidesSwapped = true;
                this.sides = sides.toArray(new ModelPart.Quad[4]);
            }
        }
    }

    @Override
    public ModelPart.Quad[] getSides() {
        return this.sides;
    }

    @Override
    public void setSides(ModelPart.Quad[] sides) {
        isSidesSwapped = true;
        this.sides = sides;
    }

    @Override
    public void resetSides() {
        this.sides = this.originalQuads;
        isSidesSwapped = false;
    }
}
