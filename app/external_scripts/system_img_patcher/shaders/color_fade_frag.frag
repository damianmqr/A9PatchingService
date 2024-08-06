#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES texUnit;
uniform float opacity;
uniform float gamma;
varying vec2 UV;

void main() {{
    vec4 color = texture2D(texUnit, UV);
    vec2 adjustedUV = vec2(UV.x, UV.y * 2.0);

    float dist = distance(adjustedUV, vec2({center_x}, {center_y}));

    float innerDist = distance(adjustedUV, vec2({round(center_x + radius * 0.67, 3)}, {round(center_y - radius * 0.23, 3)}));

    if (dist < {radius} && innerDist > {round(radius * 0.95, 3)}) {{
        vec3 rgb2 = pow(color.rgb * opacity * {opacity}, vec3(gamma));
        gl_FragColor = vec4(rgb2, 1.0);
    }} else {{
        vec3 rgb = pow(color.rgb * opacity, vec3(gamma));
        gl_FragColor = vec4(rgb, 1.0);
    }}
}}