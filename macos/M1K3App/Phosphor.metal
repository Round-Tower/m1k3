//
//  Phosphor.metal
//  M1K3App
//
//  RealityKit CustomMaterial surface shader: a fresnel/rim emissive over a
//  near-black body — the "phosphor skin" for the animated 3D companion (the fox).
//  The edge-box wireframe (scratch/phosphor-spike) can't follow a SKINNED mesh;
//  a per-fragment surface shader is blind to the skeleton, so it rides any
//  animation for free. Bright at grazing angles (the silhouette/edges), dark
//  facing the camera — the same additive-glow lattice as the site, on a creature.
//
//  Lives in the APP target (not a SwiftPM package) because SwiftPM does not
//  compile .metal; Xcode compiles this into the app's default.metallib, loaded
//  via Bundle.main (see PhosphorMaterial.swift). Validated host-independent with
//  `xcrun -sdk macosx metal -c` against the RealityKit headers.
//
//  The look is data-driven: colour (xyz) + fresnel power (w) arrive via the
//  material's custom_parameter, so the app can shift it with M1K3's state
//  (PhosphorTreatment) without recompiling the shader.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85 (shader compiles
//  against the RealityKit metal headers; render quality is verify-at-⌘R).
//  Prior: Unknown.
//

#include <metal_stdlib>
#include <RealityKit/RealityKit.h>

using namespace metal;

[[visible]]
void phosphorSurface(realitykit::surface_parameters params)
{
    auto surface = params.surface();
    auto geometry = params.geometry();

    // Colour + fresnel power supplied by the app (PhosphorTreatment.customValue).
    float4 custom = params.uniforms().custom_parameter();
    float3 phosphor = custom.xyz;
    float power = max(custom.w, 0.5); // guard against an unset (zero) parameter

    // Fresnel: 1 at grazing angles, 0 facing the camera. Raised to `power` so the
    // glow hugs the silhouette/edges (the phosphor lattice feel).
    float3 n = normalize(geometry.normal());
    float3 view = normalize(geometry.view_direction());
    float facing = saturate(dot(n, view));
    float rim = pow(1.0 - facing, power);

    // A faint constant floor so the body reads as a dark phosphor shell, not a
    // void, between the bright edges.
    float glow = rim + 0.04;

    surface.set_base_color(half3(0.02h, 0.04h, 0.03h)); // near-black body
    surface.set_emissive_color(half3(phosphor) * half(glow));
    surface.set_metallic(0.0h);
    surface.set_roughness(1.0h);
}

// Cel / toon shading — an ADAPTATION of the creature's own texture, not a flat
// repaint. Samples the original base-colour texture (preserved by building the
// CustomMaterial from the baked material), posterises it into flat bands, and
// lights it with quantised N·L + an ink silhouette. Keeps the fox's fur identity
// while reading unmistakably "toon" — distinct from the phosphor glow. A faint
// state tint from custom_parameter keeps it shifting with M1K3's mood.
[[visible]]
void celSurface(realitykit::surface_parameters params)
{
    auto surface = params.surface();
    auto geometry = params.geometry();
    auto tex = params.textures();

    constexpr sampler bilinear(coord::normalized, address::repeat, filter::linear, mip_filter::linear);
    float2 uv = geometry.uv0();

    // The creature's OWN colour (texture × material tint), posterised into bands.
    half3 albedo = tex.base_color().sample(bilinear, uv).rgb;
    half3 fur = albedo * half3(params.material_constants().base_color_tint());
    half3 poster = floor(fur * 4.0h) / 4.0h;

    // Toon lighting: our own key light, N·L quantised into hard steps with an
    // ambient floor so the shadow side keeps the fur colour, not black.
    float3 n = normalize(geometry.normal());
    float3 light = normalize(float3(0.4, 0.6, 0.8));
    float ndl = saturate(dot(n, light));
    float toon = max(floor(ndl * 3.0) / 3.0, 0.45);

    // Subtle mood tint (kept low so the fur identity dominates — Kev's ask).
    // xyz = state tint (PhosphorTreatment.{red,green,blue}); .w (fresnelPower) unused here.
    // 1.4 lifts the sub-1.0 treatment colours so the 20% tint stays additive, not darkening.
    half3 state = half3(params.uniforms().custom_parameter().xyz);
    half3 shaded = mix(poster, poster * state * 1.4h, 0.2h) * half(toon);

    // Ink silhouette: hard black rim at grazing angles. 2.5 tightens the rim;
    // 0.62 is the threshold past which the grazing edge becomes ink-black.
    float3 view = normalize(geometry.view_direction());
    float ink = step(0.62, pow(1.0 - saturate(dot(n, view)), 2.5));

    surface.set_base_color(half3(0.0h));
    surface.set_emissive_color(mix(shaded, half3(0.0h), half(ink)));
    surface.set_metallic(0.0h);
    surface.set_roughness(1.0h);
}
