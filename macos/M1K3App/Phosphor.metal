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

// Cel / toon shading — the creature's OWN texture, lit in flat cartoon bands with
// a bold ink outline. The cel signature is banding the LIGHT, not the colour:
// posterising the albedo fragments the fur into camo blotches, so we keep the fur
// faithful and quantise N·L into a few BRIGHT steps (cel is bold + flat, never
// muddy). Distinct from phosphor's monochrome glow; not state-reactive (the fur is
// the point — phosphor is the reactive one).
[[visible]]
void celSurface(realitykit::surface_parameters params)
{
    auto surface = params.surface();
    auto geometry = params.geometry();
    auto tex = params.textures();

    constexpr sampler bilinear(coord::normalized, address::repeat, filter::linear, mip_filter::linear);
    float2 uv = geometry.uv0();

    // The creature's OWN fur colour, kept faithful (NOT posterised).
    half3 fur = tex.base_color().sample(bilinear, uv).rgb
        * half3(params.material_constants().base_color_tint());

    // Toon lighting: three BRIGHT flat bands (lit / mid / shadow). The shadow band
    // is still 0.62 so the fur colour reads everywhere — cartoon, not gloom.
    float3 n = normalize(geometry.normal());
    float3 light = normalize(float3(0.35, 0.7, 0.9));
    float ndl = saturate(dot(n, light));
    float toon = ndl > 0.66h ? 1.0h : (ndl > 0.33h ? 0.82h : 0.62h);

    // Bold ink outline at the silhouette — the defining cel line. Wider/softer than
    // the phosphor rim (threshold 0.78, gentle power 1.6) so it reads as a drawn edge.
    float3 view = normalize(geometry.view_direction());
    float ink = step(0.78, pow(1.0 - saturate(dot(n, view)), 1.6));

    half3 shaded = fur * half(toon);
    surface.set_base_color(half3(0.0h));
    surface.set_emissive_color(mix(shaded, half3(0.0h), half(ink)));
    surface.set_metallic(0.0h);
    surface.set_roughness(1.0h);
}
